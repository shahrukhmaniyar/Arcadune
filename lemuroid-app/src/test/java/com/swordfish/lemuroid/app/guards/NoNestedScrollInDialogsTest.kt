package com.swordfish.lemuroid.app.guards

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against the crash shipped in 1.1.0: ArcaduneDialog already scrolls its content, and a
 * second `verticalScroll` (or a list without a height limit) inside it makes Compose throw
 * "Vertically scrollable component was measured with an infinity maximum height" and the app closes.
 *
 * This reads the source code, so it runs in a second on any PC, without a phone.
 */
class NoNestedScrollInDialogsTest {
    private val sourceRoot = File("src/main/java")

    @Test
    fun dialogsNeverNestAScrollInsideTheirOwnScroll() {
        assertTrue("Source folder not found from ${File(".").absolutePath}", sourceRoot.isDirectory)

        val problems = mutableListOf<String>()
        var dialogsChecked = 0
        sourceRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            for (call in dialogCalls(text)) {
                dialogsChecked++
                val scrollable = !Regex("""scrollable\s*=\s*false""").containsMatchIn(call.arguments)
                if (!scrollable) continue
                val line = text.substring(0, call.start).count { it == '\n' } + 1
                if ("verticalScroll(" in call.content) {
                    problems += "${file.name}:$line ArcaduneDialog content uses verticalScroll " +
                        "(the dialog already scrolls)"
                }
                Regex("""(LazyColumn|LazyVerticalGrid|LazyVerticalStaggeredGrid)\s*\(([^\n]*)""")
                    .findAll(call.content)
                    .filter { "heightIn(max" !in it.groupValues[2] && "height(" !in it.groupValues[2] }
                    .forEach {
                        problems += "${file.name}:$line ArcaduneDialog content has a ${it.groupValues[1]} " +
                            "without a height limit"
                    }
            }
        }

        assertTrue("No ArcaduneDialog was found: the check is not looking at the right files", dialogsChecked > 5)
        assertTrue("Nested scrolling in dialogs (crashes the app):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private class DialogCall(val start: Int, val arguments: String, val content: String)

    /** Every `ArcaduneDialog(...) { ... }` call with its arguments and its content block. */
    private fun dialogCalls(text: String): List<DialogCall> {
        val calls = mutableListOf<DialogCall>()
        var index = text.indexOf("ArcaduneDialog(")
        while (index >= 0) {
            val isDefinition = text.substring(maxOf(0, index - 4), index) == "fun "
            val argsStart = index + "ArcaduneDialog(".length
            val argsEnd = matching(text, argsStart - 1, '(', ')')
            if (!isDefinition && argsEnd > 0) {
                var cursor = argsEnd + 1
                while (cursor < text.length && text[cursor].isWhitespace()) cursor++
                val content =
                    if (cursor < text.length && text[cursor] == '{') {
                        val end = matching(text, cursor, '{', '}')
                        if (end > 0) text.substring(cursor, end) else ""
                    } else {
                        ""
                    }
                calls += DialogCall(index, text.substring(argsStart, argsEnd), content)
            }
            index = text.indexOf("ArcaduneDialog(", index + 1)
        }
        return calls
    }

    private fun matching(
        text: String,
        open: Int,
        openChar: Char,
        closeChar: Char,
    ): Int {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
