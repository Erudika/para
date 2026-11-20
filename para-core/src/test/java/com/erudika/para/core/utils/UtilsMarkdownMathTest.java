/*
 * Copyright 2013-2026 Erudika. https://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.core.utils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class UtilsMarkdownMathTest {

	@Test
	void preservesMultilineMathSourceBeforeMarkdownRendersIt() {
		String markdown = "$$\n\\begin{bmatrix}\na & b \\\\\nc & d\n\\end{bmatrix}\n$$";
		String html = Utils.markdownToHtml(markdown, false);

		assertTrue(html.contains("<span class=\"math-source\">"), html);
		assertTrue(html.contains("$$\n\\begin{bmatrix}\na &amp; b \\\\\nc &amp; d\n\\end{bmatrix}\n$$"), html);
	}

	@Test
	void preservesGroupedFractionsInMathSource() {
		String markdown = "$$\\sigma \\leq 0,21 f^{2/3} - 6 \\dfrac{M-M}{h^2}$$";
		String html = Utils.markdownToHtml(markdown, false);

		assertTrue(html.contains("<span class=\"math-source\">"), html);
		assertTrue(html.contains("\\dfrac{M-M}{h^2}"), html);
	}

	@Test
	void preservesAllDefaultMathDelimiters() {
		List<String[]> delimiters = List.of(
				new String[]{"$$", "$$"},
				new String[]{"\\(", "\\)"},
				new String[]{"\\begin{equation}", "\\end{equation}"},
				new String[]{"\\begin{align}", "\\end{align}"},
				new String[]{"\\begin{alignat}", "\\end{alignat}"},
				new String[]{"\\begin{gather}", "\\end{gather}"},
				new String[]{"\\begin{CD}", "\\end{CD}"},
				new String[]{"\\[", "\\]"});

		for (String[] delimiter : delimiters) {
			String markdown = delimiter[0] + "\nx^2\n" + delimiter[1];
			String html = Utils.markdownToHtml(markdown, false);

			assertTrue(html.contains("<span class=\"math-source\">" + markdown + "</span>"), html);
		}
	}

	@Test
	void doesNotTreatMathInsideFencedCodeAsFormula() {
		String markdown = "```latex\n$$\nx^2\n$$\n```";
		String html = Utils.markdownToHtml(markdown, false);

		assertFalse(html.contains("math-source"), html);
		assertTrue(html.contains("<pre>"), html);
	}

	@Test
	void leavesNormalMarkdownParagraphsToTheDefaultRenderer() {
		String html = Utils.markdownToHtml("A **strong** word.", false);

		assertFalse(html.contains("math-source"), html);
		assertTrue(html.contains("<strong>strong</strong>"), html);
	}

	@Test
	void leavesInlineMathInProseToTheDefaultRenderer() {
		String html = Utils.markdownToHtml("Formula: $$x^2$$.", false);

		assertFalse(html.contains("math-source"), html);
		assertTrue(html.contains("Formula:"), html);
	}
}
