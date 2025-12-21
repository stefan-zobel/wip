package misc;

public final class XmlUtils {

    /**
     * Searches for an attribute value without memory allocations inside the
     * loop.
     */
    public static String findAttributeValue(String xml, String attrName, int startIndex) {
        if (xml == null || attrName == null)
            return "";

        int attrNameLen = attrName.length();
        if (startIndex < 0 || startIndex >= xml.length() || attrNameLen == 0) {
            return "";
        }

        int currentPos = startIndex;
        int xmlLen = xml.length();

        while (currentPos < xmlLen) {
            // 1. Find next occurrence
            int absoluteIndex = xml.indexOf(attrName, currentPos);
            if (absoluteIndex == -1)
                break;

            // 2. Word Boundary Check
            if (absoluteIndex > 0) {
                char prevChar = xml.charAt(absoluteIndex - 1);
                if (!(prevChar <= ' ') && prevChar != '<') {
                    currentPos = absoluteIndex + attrNameLen;
                    continue;
                }
            }

            int nextSearchStart = absoluteIndex + attrNameLen;

            // 3. Skip whitespace manually to avoid substring/trim allocations
            int spaces = 0;
            while (nextSearchStart + spaces < xmlLen && xml.charAt(nextSearchStart + spaces) <= ' ') {
                spaces++;
            }

            int afterAttrPos = nextSearchStart + spaces;

            // 4. Check for '='
            if (afterAttrPos < xmlLen && xml.charAt(afterAttrPos) == '=') {
                int afterEqualsPos = afterAttrPos + 1;

                // Skip whitespace after '='
                int spaces2 = 0;
                while (afterEqualsPos + spaces2 < xmlLen && xml.charAt(afterEqualsPos + spaces2) <= ' ') {
                    spaces2++;
                }

                int quotePos = afterEqualsPos + spaces2;

                if (quotePos < xmlLen) {
                    char quote = xml.charAt(quotePos);
                    if (quote == '"' || quote == '\'') {
                        int valueStart = quotePos + 1;
                        int endQuote = xml.indexOf(quote, valueStart);

                        if (endQuote != -1) {
                            // Found it!
                            return xml.substring(valueStart, endQuote);
                        }
                    }
                }
                // Skip past the '=' we already processed
                nextSearchStart = afterEqualsPos + spaces2;
            }

            // Advance
            currentPos = nextSearchStart;
        }

        return "";
    }

    private XmlUtils() {
    }
}
