/*******************************************************************************
 * Copyright (c) 2017 Buddhist Digital Resource Center (BDRC)
 * 
 * If this file is a derivation of another work the license header will appear 
 * below; otherwise, this work is licensed under the Apache License, Version 2.0 
 * (the "License"); you may not use this file except in compliance with the 
 * License.
 * 
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package io.bdrc.lucene.sa;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.lucene.analysis.CharacterUtils;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.CharacterUtils.CharacterBuffer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import io.bdrc.lucene.stemmer.Optimizer;
import io.bdrc.lucene.stemmer.Row;
import io.bdrc.lucene.stemmer.Trie;

/**
 * A maximal-matching word tokenizer for Tibetan that uses a {@link Trie}.
 * 
 * <p>
 * Takes a syllable at a time and returns the longest sequence of syllable that form a word within the Trie.<br>
 * {@link #isTibLetter(int)} is used to distinguish clusters of letters forming syllables and {@code u\0F0B}(tsheg) to distinguish syllables within a word.
 * <br> 
 *  - Unknown syllables are tokenized as separate words.
 * <br>
 *  - All the punctuation is discarded from the produced tokens 
 * <p>
 * Due to its design, this tokenizer doesn't deal with contextual ambiguities.<br>
 * For example, (...)   
 * 
 * Derived from Lucene 6.4.1 analysis.
 * 
 * @author Élie Roux
 * @author Drupchen
 *
 */
public final class SkrtWordTokenizer extends Tokenizer {
	private Trie scanner;

	// this tokenizer generates three attributes:
	// term offset, positionIncrement and type
	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
	private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

	/**
	 * Constructs a SkrtWordTokenizer using the file designed by filename
	 * @param filename
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public SkrtWordTokenizer(String filename) throws FileNotFoundException, IOException {
		init(filename);
	}

	/**
	 * Constructs a SkrtWordTokenizer using a default lexicon file (here "resources/sanskrit-stemming-data/output/total_output.txt") 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public SkrtWordTokenizer() throws FileNotFoundException, IOException {
		init("resources/sanskrit-stemming-data/output/total_output.txt");
	}

	/**
	 * Initializes and populates {@see #scanner} 
	 * 
	 * The format of each line in filename must be as follows: "<sandhied_inflected_form>,<initial>,<diffs>/<initial_diff>"
	 * @param filename the file containing the entries to be added
	 * @throws FileNotFoundException 
	 * @throws IOException
	 */
	private void init(String filename) throws FileNotFoundException, IOException {
		this.scanner = new Trie(true);

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
			String line;
			while ((line = br.readLine()) != null) {
				int endOfFormIndex = line.indexOf(',');
				if (endOfFormIndex == -1) {
					throw new IllegalArgumentException("The dictionary file is corrupted in the following line.\n" + line);
				} else {
					this.scanner.add(line.substring(0, endOfFormIndex), line.substring(endOfFormIndex+1));
				}
			}
			Optimizer opt = new Optimizer();
			this.scanner.reduce(opt);
		}
	}

	private int offset = 0, bufferIndex = 0, dataLen = 0, finalOffset = 0;
	private static final int MAX_WORD_LEN = 255;
	private static final int IO_BUFFER_SIZE = 4096;

	//	  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
	//	  private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

	private final CharacterBuffer ioBuffer = CharacterUtils.newCharacterBuffer(IO_BUFFER_SIZE);

	private ArrayList<String> extraTokens;

	private boolean emitExtraTokens;

	private ArrayList<String> initialDiff = null;

	/**
	 * Called on each token character to normalize it before it is added to the
	 * token. The default implementation does nothing. Subclasses may use this to,
	 * e.g., lowercase tokens.
	 */
	protected int normalize(int c) {
		return c;
	}

	@Override
	public final boolean incrementToken() throws IOException {
		//System.out.println("\nincrement token\n");
		clearAttributes();
		
		if (emitExtraTokens) {
			addExtraToken();
			return true;
		}
		
		int length = 0;
		int start = -1; // this variable is always initialized
		int end = -1;
		int confirmedEnd = -1;
		int confirmedEndIndex = -1;
		String cmd = null;
		int w = -1;
		int cmdIndex = -1;
		int potentialEndCmdIndex = -1;
		boolean potentialEnd = false;
		Row now = null;
		char[] buffer = termAtt.buffer();
		while (true) {
			if (bufferIndex >= dataLen) {
				offset += dataLen;
				CharacterUtils.fill(ioBuffer, input); // read supplementary char aware with CharacterUtils
				if (ioBuffer.getLength() == 0) {
					dataLen = 0; // so next offset += dataLen won't decrement offset
					if (length > 0) {
						break;
					} else {
						finalOffset = correctOffset(offset);
						return false;
					}
				}
				dataLen = ioBuffer.getLength();
				bufferIndex = 0;
			}
			// use CharacterUtils here to support < 3.1 UTF-16 code unit behavior if the char based methods are gone
			final int c = Character.codePointAt(ioBuffer.getBuffer(), bufferIndex, ioBuffer.getLength());
			final int charCount = Character.charCount(c);
			bufferIndex += charCount;

			if (SkrtSylTokenizer.isSLP(c)) {  // if it's a token char
				if (length == 0) {                // start of token
					assert(start == -1);
					now = scanner.getRow(scanner.getRoot());
					cmdIndex = now.getCmd((char) c);
					potentialEnd = (cmdIndex >= 0); // we may have caught the end, but we must check if next character is a tsheg
					if (potentialEnd) {
						potentialEndCmdIndex = cmdIndex;
					}
					w = now.getRef((char) c);
					now = (w >= 0) ? scanner.getRow(w) : null;
					start = offset + bufferIndex - charCount;
					end = start + charCount;
				} else {
					if (length >= buffer.length-1) { // check if a supplementary could run out of bounds
						buffer = termAtt.resizeBuffer(2+length); // make sure a supplementary fits in the buffer
					}
					if (now != null) {
						end += charCount;
						cmdIndex = now.getCmd((char) c);
						potentialEnd = (cmdIndex >= 0); // we may have caught the end, but we must check if next character is a tsheg
						if (potentialEnd) {
							potentialEndCmdIndex = cmdIndex;
						}
						w = now.getRef((char) c);
						now = (w >= 0) ? scanner.getRow(w) : null;
					}
				}
				length += Character.toChars(normalize(c), buffer, length); // buffer it, normalized
				if (length >= MAX_WORD_LEN) { // buffer overflow! make sure to check for >= surrogate pair could break == test
					break;
				}
				if (potentialEnd) {
					break;
				}
			} else if (length > 0) {           // at non-Letter w/ chars
				break;                           // return 'em
			}
		}
		if (potentialEnd) {
			confirmedEnd = end;
			confirmedEndIndex = bufferIndex;
		}
		if (confirmedEnd > 0) {
			bufferIndex = confirmedEndIndex;
			end = confirmedEnd;
		}
		
		termAtt.setLength(end - start);
		cmd = scanner.getCommandVal(potentialEndCmdIndex);
		if (cmd != null) {
			extraTokens = reconstructLemmas(cmd, termAtt.toString());
			if (extraTokens.size() != 0) {
				emitExtraTokens = true;
			}
		}
		assert(start != -1);
		finalOffset = correctOffset(end);
		offsetAtt.setOffset(correctOffset(start), finalOffset);
		return true;
	}
	
	private void addExtraToken() {
		if (extraTokens.size() > 0) {
			termAtt.setEmpty().append(extraTokens.get(0).toString());
			extraTokens.remove(0);
			if (extraTokens.size() == 0) {
				emitExtraTokens = false;
			}
		}
	}
	
	private ArrayList<String> reconstructLemmas(String cmd, String inflected) {
		/**
		 * Reconstructs all the possible sandhied strings for the first word using CmdParser.parse(), 
		 * iterates through them, checking if the sandhied string is found in the sandhiable range,
		 * only reconstructs the lemmas if there is a match.
		 * 
		 * 
		 * Maximum range of characters where sandhi applies:
		 * - vowel sandhi          : currentCharacter   - currentCharacter+2 (ex. "-O a-"/"-Oa-"  => "-Ava-")
		 * - consonant sandhi1     : currentCharacter   - currentCharacter+2 (ex. "-k y-"/"-ky-"  => "-g y-"/"-gy-")
		 * - consonant sandhi2     : currentCharacter   - currentCharacter+3 (ex. "-n W-"/"-nW-"  => "-Mz W-"/"-MzW-")
		 * - visarga sandhi1       : currentCharacter-1 - currentCharacter+2 (ex. "-aH A-"/"-aHA" => "-A A-"/"-AA")
		 * - visarga sandhi2       : currentCharacter-1 - currentCharacter+2 (ex. "-aH c-"/"-aHc" => "-aS c-"/"-aSc-")
		 * - absolute finals sandhi: currentCharacter   - currentCharacter+X (X = consonant cluster ending a word. only one consonant remains)
		 * - cC words sandhi       : currentCharacter   - currentCharacter+3 (ex. "-a c-"/"-ac-"  => "-a cC-"/"-acC-")
		 * - punar sandhi          : currentCharacter   - currentCharacter+2 (ex. "-r k-"="-rk-"  => "-H k-"/"-Hk-")
		 * The range goes from -1 to +3. TODO: ask to Charles the maximum X can be.
		 * 
		 * @return: the list of all the possible lemmas given the current context
		 */
		HashMap<String, Boolean> totalLemmas = new HashMap<String, Boolean>();
		int inputBufferIndex = bufferIndex-1; // at this stage, bufferIndex has already been incremented
		String sandhiableRange = String.valueOf(Arrays.copyOfRange(ioBuffer.getBuffer(), inputBufferIndex-1, inputBufferIndex+3)); 
		String[] t = new String[0];
		
		HashMap<String, ArrayList<String>> parsedCmd = CmdParser.parse(inflected.substring(inflected.length()-1), cmd);
		for (Entry<String, ArrayList<String>> current: parsedCmd.entrySet()) {
			String sandhied = current.getKey();
			
			if (sandhiableRange.contains(sandhied)) {
				ArrayList<String> diffs = current.getValue(); 
				for (String lemma: diffs) {
					t = lemma.split("\\+");
					assert(t.length == 2); // all lemmas should contain +
					int toDelete = Integer.parseInt(t[0]);
					t = t[1].split(",");
					String toAdd = t[0];
					String newInitial = "";
					if (t.length == 2) {
						newInitial = t[1]; // TODO: needs to be a possible first element of termAtt#buffer on next iteration of incrementToken() 
					}
					String reconstructedLemma = inflected.substring(0, inflected.length()-toDelete)+toAdd;
					totalLemmas.put(reconstructedLemma, true);
				}
			}
		}
		ArrayList<String> output = new ArrayList<String>(totalLemmas.keySet());
		return output;
	}

	@Override
	public final void end() throws IOException {
		super.end();
		// set final offset
		offsetAtt.setOffset(finalOffset, finalOffset);
	}

	@Override
	public void reset() throws IOException {
		super.reset();
		bufferIndex = 0;
		offset = 0;
		dataLen = 0;
		finalOffset = 0;
		ioBuffer.reset(); // make sure to reset the IO buffer!!
		
		// for emitting multiple tokens
		emitExtraTokens = false;
	}
}
