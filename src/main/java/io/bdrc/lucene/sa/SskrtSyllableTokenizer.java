/*******************************************************************************
 * Copyright (c) 2017 Buddhist Digital Resource Center (BDRC)
 * 
 * If this file is a derivation of another work the license header will appear 
 * below; otherwise, this work is licensed under the Apache License, Version 2.0 
 * (the 'License"); you may not use this file except in compliance with the 
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

import java.util.HashMap;

import org.apache.lucene.analysis.util.CharTokenizer;

/** 
 * <p>
 * Derived from Lucene 6.4.1 analysis.core.WhitespaceTokenizer.java
 * </p>
 * 
 * @author Hélios Hildt
 * 
 */
public final class SskrtSyllableTokenizer extends CharTokenizer {
  
	/**
	 * Construct a new TibSyllableTokenizer.
	 */
	public SskrtSyllableTokenizer() {
	}
	
	private static final HashMap<Character, Character> charType = createMap();
	private static final HashMap<Character, Character> createMap()
	{
		HashMap<Character, Character> charType = new HashMap<Character, Character>();
		// vowels
		charType.put('a', 'V');
		charType.put('A', 'V');
		charType.put('i', 'V');
		charType.put('I', 'V');
		charType.put('u', 'V');
		charType.put('U', 'V');
		charType.put('f', 'V');
		charType.put('F', 'V');
		charType.put('x', 'V');
		charType.put('X', 'V');
		charType.put('e', 'V');
		charType.put('E', 'V');
		charType.put('o', 'V');
		charType.put('O', 'V');
		// special class for anusvara & visarga, jihvamuliya, upadhmaniya
		charType.put('M', 'X');
		charType.put('H', 'X');
		charType.put('V', 'X');
		charType.put('Z', 'X');
		// consonants
		charType.put('k', 'C');
		charType.put('K', 'C');
		charType.put('g', 'C');
		charType.put('G', 'C');
		charType.put('N', 'C');
		charType.put('c', 'C');
		charType.put('C', 'C');
		charType.put('j', 'C');
		charType.put('J', 'C');
		charType.put('Y', 'C');
		charType.put('w', 'C');
		charType.put('W', 'C');
		charType.put('q', 'C');
		charType.put('Q', 'C');
		charType.put('R', 'C');
		charType.put('t', 'C');
		charType.put('T', 'C');
		charType.put('d', 'C');
		charType.put('D', 'C');
		charType.put('n', 'C');
		charType.put('p', 'C');
		charType.put('P', 'C');
		charType.put('b', 'C');
		charType.put('B', 'C');
		charType.put('m', 'C');
		charType.put('y', 'C');
		charType.put('r', 'C');
		charType.put('l', 'C');
		charType.put('L', 'C');
		charType.put('|', 'C');
		charType.put('v', 'C');
		charType.put('S', 'C');
		charType.put('z', 'C');
		charType.put('s', 'C');
		charType.put('h', 'C');
		return charType;
	}

	public boolean isSylEnd(char char1, char char2) {
		// The existence of both chars in the HashMap is assumed
		if (charType.get(char1) != 'C' && charType.get(char2) == 'C') {
			return true;
		} else {
			return false;
		}
	}
  
	/** 
	 * Collects only characters which satisfy isTibetanLetterOrDigit()
	 * @return 
	 */
	@Override
	protected boolean isTokenChar(int c) {
		return false;
	}
}