package blocky_game;

import blocky.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep module for mapping between Blockly workspace structures (JSON / XML)
 * and EMF AST entities (Body, Container, Statement).
 *
 * Encapsulates block type recognition, case-insensitive condition/direction parsing,
 * AST reconstruction, block ID identity tracking, and Blockly XML serialization.
 */
public class BlockyAstMapper {

    private final IdentityHashMap<Statement, String> stmtToBlocklyId = new IdentityHashMap<>();

    /**
     * Clears existing statement-to-block ID mappings.
     */
    public void clearMappings() {
        stmtToBlocklyId.clear();
    }

    /**
     * Looks up the Blockly block ID that produced a specific EMF Statement.
     */
    public String getBlocklyId(Statement stmt) {
        if (stmt == null) return null;
        return stmtToBlocklyId.get(stmt);
    }

    /**
     * Registers an explicit statement-to-block ID mapping.
     */
    public void mapStatementId(Statement stmt, String blockId) {
        if (stmt != null && blockId != null && !blockId.isEmpty()) {
            stmtToBlocklyId.put(stmt, blockId);
        }
    }

    /**
     * Rebuilds an EMF Body solution from Blockly JSON block descriptor data.
     * Maps each Statement to its source Blockly block ID.
     *
     * @param blockData list of root block dictionaries
     * @return the built Body, or an empty Body if blockData is null or empty
     */
    public Body rebuildProgram(List<Map<String, Object>> blockData) {
        clearMappings();
        if (blockData == null || blockData.isEmpty()) {
            return null;
        }
        return buildSolutionBody(blockData);
    }

    private Body buildSolutionBody(List<Map<String, Object>> dataList) {
        Body body = BlockyFactory.eINSTANCE.createBody();
        if (dataList == null || dataList.isEmpty()) {
            body.setFirstContainer(null);
            return body;
        }

        Container first = null;
        Container last = null;

        for (Map<String, Object> data : dataList) {
            Container chainHead = createContainerChainFromData(data);
            if (chainHead == null) continue;

            if (first == null) {
                first = chainHead;
                last = chainHead;
            } else {
                last.setNext(chainHead);
            }

            Container cur = chainHead;
            while (cur.getNext() != null) {
                cur = cur.getNext();
            }
            last = cur;
        }

        body.setFirstContainer(first);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Container createContainerChainFromData(Map<String, Object> data) {
        if (data == null) return null;

        Statement stmt = createStatementFromData(data);
        if (stmt == null) return null;

        Container c = BlockyFactory.eINSTANCE.createContainer();
        c.setStatement(stmt);

        Map<String, Object> nextData = (Map<String, Object>) data.get("next");
        if (nextData != null) {
            c.setNext(createContainerChainFromData(nextData));
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private Statement createStatementFromData(Map<String, Object> data) {
        String type = (String) data.get("type");
        String lowerType = type != null ? type.toLowerCase() : "";
        String blockId = null;
        try {
            Object idObj = data.get("id");
            blockId = idObj != null ? String.valueOf(idObj) : null;
        } catch (Exception ignored) {
        }
        Statement stmt = null;

        if ("maze_moveforward".equals(lowerType) || "move_forward".equals(lowerType)) {
            AtomicStatement a = BlockyFactory.eINSTANCE.createAtomicStatement();
            a.setKind(AtomicStatementKind.MOVE_FORWARD);
            stmt = a;
        } else if ("maze_turn".equals(lowerType) || "turn_left".equals(lowerType) || "turn_right".equals(lowerType)) {
            String dir = (String) data.get("DIR");
            if (dir == null) {
                dir = type;
            }
            boolean right = dir != null && dir.toLowerCase().contains("right");
            AtomicStatement a = BlockyFactory.eINSTANCE.createAtomicStatement();
            a.setKind(right ? AtomicStatementKind.TURN_RIGHT : AtomicStatementKind.TURN_LEFT);
            stmt = a;
        } else if ("maze_forever".equals(lowerType) || "repeat_until_goal".equals(lowerType)) {
            Loop r = BlockyFactory.eINSTANCE.createLoop();
            Map<String, Object> bodyData = (Map<String, Object>) data.get("body");
            r.setBody(bodyData != null ? wrapContainerChain(createContainerChainFromData(bodyData)) : emptyBody());
            stmt = r;
        } else if (lowerType.startsWith("maze_if") || lowerType.startsWith("if_path")) {
            IfStmt i = BlockyFactory.eINSTANCE.createIfStmt();
            String dir = (String) data.get("DIR");
            ConditionKind ck = dir != null ? parseCondition(dir) : parseCondition(type);
            i.setCondition(ck);
            Map<String, Object> thenData = (Map<String, Object>) data.get("body");
            if (thenData == null) {
                thenData = (Map<String, Object>) data.get("then");
            }
            i.setThenBody(wrapContainerChain(thenData != null ? createContainerChainFromData(thenData) : null));
            Map<String, Object> elseData = (Map<String, Object>) data.get("elseBranch");
            if (elseData == null) {
                elseData = (Map<String, Object>) data.get("else");
            }
            if (elseData != null) {
                i.setElseBody(wrapContainerChain(createContainerChainFromData(elseData)));
            }
            stmt = i;
        }

        if (stmt != null && blockId != null && !blockId.isEmpty()) {
            stmtToBlocklyId.put(stmt, blockId);
        }
        return stmt;
    }

    private static Body emptyBody() {
        return BlockyFactory.eINSTANCE.createBody();
    }

    private static Body wrapContainerChain(Container head) {
        Body b = BlockyFactory.eINSTANCE.createBody();
        b.setFirstContainer(head);
        return b;
    }

    /**
     * Parses a condition string (e.g. from block DIR field or type) in a case-insensitive manner.
     */
    public static ConditionKind parseCondition(String type) {
        if (type == null) return ConditionKind.CHECK_FORWARD;
        String lower = type.toLowerCase();
        if (lower.contains("forward") || lower.contains("ahead"))
            return ConditionKind.CHECK_FORWARD;
        if (lower.contains("left"))
            return ConditionKind.CHECK_LEFT;
        if (lower.contains("right"))
            return ConditionKind.CHECK_RIGHT;
        return ConditionKind.CHECK_FORWARD;
    }

    /**
     * Converts a linked list of {@link Container}s to Blockly XML (without outer &lt;xml&gt;).
     */
    public String statementChainToXml(Container first) {
        if (first == null) return "";
        StringBuilder sb = new StringBuilder();
        appendStatementXml(first, null, sb);
        return sb.toString();
    }

    /**
     * Returns a full Blockly XML document for the level's solution (with outer &lt;xml&gt;).
     */
    public String solutionToBlocklyXml(Level level) {
        if (level == null || level.getSolution() == null) {
            return "<xml></xml>";
        }
        Container first = level.getSolution().getFirstContainer();
        if (first == null) return "<xml></xml>";

        String inner = statementChainToXml(first);
        String xml = "<xml xmlns=\"https://developers.google.com/blockly/xml\">" + inner + "</xml>";
        if (xml.contains("<block ")) {
            xml = xml.replaceFirst("<block ", "<block x=\"70\" y=\"70\" ");
        }
        return xml;
    }

    private void appendStatementXml(Container c, Container overrideNext, StringBuilder sb) {
        if (c == null) return;
        Statement stmt = c.getStatement();
        Container next = overrideNext != null ? overrideNext : c.getNext();
        if (stmt == null) {
            if (next != null) {
                appendStatementXml(next, null, sb);
            }
            return;
        }

        if (stmt instanceof AtomicStatement) {
            AtomicStatement a = (AtomicStatement) stmt;
            AtomicStatementKind kind = a.getKind();
            if (kind == null) {
                kind = AtomicStatementKind.TURN_LEFT;
            }
            if (kind.getValue() == AtomicStatementKind.MOVE_FORWARD_VALUE) {
                sb.append("<block type=\"maze_moveForward\">");
            } else if (kind.getValue() == AtomicStatementKind.TURN_LEFT_VALUE) {
                sb.append("<block type=\"maze_turn\"><field name=\"DIR\">turnLeft</field>");
            } else if (kind.getValue() == AtomicStatementKind.TURN_RIGHT_VALUE) {
                sb.append("<block type=\"maze_turn\"><field name=\"DIR\">turnRight</field>");
            } else {
                sb.append("<block type=\"maze_moveForward\">");
            }

            if (next != null) {
                sb.append("<next>");
                appendStatementXml(next, null, sb);
                sb.append("</next>");
            }
            sb.append("</block>");
        } else if (stmt instanceof Loop) {
            Loop r = (Loop) stmt;
            sb.append("<block type=\"maze_forever\">");
            sb.append("<statement name=\"DO\">");
            if (r.getBody() != null) {
                appendStatementXml(r.getBody().getFirstContainer(), null, sb);
            }
            sb.append("</statement>");
            sb.append("</block>");
        } else if (stmt instanceof IfStmt) {
            IfStmt i = (IfStmt) stmt;
            ConditionKind ck = i.getCondition();
            if (ck == null) {
                ck = ConditionKind.CHECK_FORWARD;
            }
            String dirField = conditionToBlocklyDir(ck);
            boolean hasElse = i.getElseBody() != null && i.getElseBody().getFirstContainer() != null;
            String blockType = hasElse ? "maze_ifElse" : "maze_if";
            sb.append("<block type=\"").append(blockType).append("\">");
            sb.append("<field name=\"DIR\">").append(escapeXml(dirField)).append("</field>");
            sb.append("<statement name=\"DO\">");
            if (i.getThenBody() != null) {
                appendStatementXml(i.getThenBody().getFirstContainer(), null, sb);
            }
            sb.append("</statement>");
            if (hasElse) {
                sb.append("<statement name=\"ELSE\">");
                appendStatementXml(i.getElseBody().getFirstContainer(), null, sb);
                sb.append("</statement>");
            }
            if (next != null) {
                sb.append("<next>");
                appendStatementXml(next, null, sb);
                sb.append("</next>");
            }
            sb.append("</block>");
        } else {
            if (next != null) {
                appendStatementXml(next, null, sb);
            }
        }
    }

    public static String conditionToBlocklyDir(ConditionKind ck) {
        if (ck == ConditionKind.CHECK_LEFT) return "isPathLeft";
        if (ck == ConditionKind.CHECK_RIGHT) return "isPathRight";
        return "isPathForward";
    }

    public static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Parses a raw Blockly workspace XML string into nested Map representations of blocks.
     */
    public static List<Map<String, Object>> parseBlocklyXml(String xml) throws Exception {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (xml == null || xml.trim().isEmpty())
            return result;

        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.w3c.dom.Element root = doc.getDocumentElement();

        org.w3c.dom.NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "block".equalsIgnoreCase(n.getNodeName())) {
                Map<String, Object> block = parseBlockElement((org.w3c.dom.Element) n);
                if (block != null) {
                    result.add(block);
                }
            }
        }
        return result;
    }

    private static Map<String, Object> parseBlockElement(org.w3c.dom.Element el) {
        if (el == null)
            return null;
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("type", el.getAttribute("type"));
        map.put("id", el.getAttribute("id"));

        org.w3c.dom.NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE)
                continue;
            org.w3c.dom.Element child = (org.w3c.dom.Element) n;
            String tag = child.getNodeName().toLowerCase();

            switch (tag) {
                case "field":
                    String fieldName = child.getAttribute("name");
                    String fieldVal = child.getTextContent().trim();
                    map.put(fieldName, fieldVal);
                    break;
                case "statement":
                    String stmtName = child.getAttribute("name");
                    org.w3c.dom.Element stmtBlock = firstBlockChild(child);
                    if (stmtBlock != null) {
                        String key = "DO".equalsIgnoreCase(stmtName) ? "body" : "ELSE".equalsIgnoreCase(stmtName) ? "elseBranch" : stmtName;
                        map.put(key, parseBlockElement(stmtBlock));
                    }
                    break;
                case "next":
                    org.w3c.dom.Element nextBlock = firstBlockChild(child);
                    if (nextBlock != null) {
                        map.put("next", parseBlockElement(nextBlock));
                    }
                    break;
                default:
                    break;
            }
        }
        return map;
    }

    private static org.w3c.dom.Element firstBlockChild(org.w3c.dom.Element parent) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node n = children.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "block".equalsIgnoreCase(n.getNodeName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        return null;
    }
}
