package blocky_game;

import blocky.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BlockyAstMapperTest {

    @Test
    void testRebuildEmptyProgram() {
        BlockyAstMapper mapper = new BlockyAstMapper();
        assertNull(mapper.rebuildProgram(null));
        assertNull(mapper.rebuildProgram(Collections.emptyList()));
    }

    @Test
    void testRebuildAtomicStatementsWithCaseInsensitivity() {
        BlockyAstMapper mapper = new BlockyAstMapper();

        Map<String, Object> moveBlock = new HashMap<>();
        moveBlock.put("type", "maze_moveForward");
        moveBlock.put("id", "blk_1");

        Map<String, Object> turnRightBlock = new HashMap<>();
        turnRightBlock.put("type", "maze_turn");
        turnRightBlock.put("DIR", "turnRight");
        turnRightBlock.put("id", "blk_2");

        moveBlock.put("next", turnRightBlock);

        Body body = mapper.rebuildProgram(Collections.singletonList(moveBlock));
        assertNotNull(body);
        assertNotNull(body.getFirstContainer());

        Container c1 = body.getFirstContainer();
        assertTrue(c1.getStatement() instanceof AtomicStatement);
        assertEquals(AtomicStatementKind.MOVE_FORWARD, ((AtomicStatement) c1.getStatement()).getKind());
        assertEquals("blk_1", mapper.getBlocklyId(c1.getStatement()));

        Container c2 = c1.getNext();
        assertNotNull(c2);
        assertTrue(c2.getStatement() instanceof AtomicStatement);
        assertEquals(AtomicStatementKind.TURN_RIGHT, ((AtomicStatement) c2.getStatement()).getKind());
        assertEquals("blk_2", mapper.getBlocklyId(c2.getStatement()));
    }

    @Test
    void testConditionParsing() {
        assertEquals(ConditionKind.CHECK_FORWARD, BlockyAstMapper.parseCondition("isPathForward"));
        assertEquals(ConditionKind.CHECK_FORWARD, BlockyAstMapper.parseCondition("path_ahead"));
        assertEquals(ConditionKind.CHECK_LEFT, BlockyAstMapper.parseCondition("isPathLeft"));
        assertEquals(ConditionKind.CHECK_RIGHT, BlockyAstMapper.parseCondition("isPathRight"));
        assertEquals(ConditionKind.CHECK_FORWARD, BlockyAstMapper.parseCondition(null));
    }

    @Test
    void testSolutionToBlocklyXml() {
        BlockyAstMapper mapper = new BlockyAstMapper();
        Level level = BlockyFactory.eINSTANCE.createLevel();

        Body body = BlockyFactory.eINSTANCE.createBody();
        Container c1 = BlockyFactory.eINSTANCE.createContainer();
        AtomicStatement stmt1 = BlockyFactory.eINSTANCE.createAtomicStatement();
        stmt1.setKind(AtomicStatementKind.MOVE_FORWARD);
        c1.setStatement(stmt1);
        body.setFirstContainer(c1);
        level.setSolution(body);

        String xml = mapper.solutionToBlocklyXml(level);
        assertNotNull(xml);
        assertTrue(xml.contains("<xml xmlns=\"https://developers.google.com/blockly/xml\">"));
        assertTrue(xml.contains("type=\"maze_moveForward\""));
        assertTrue(xml.contains("x=\"70\" y=\"70\""));
    }
}
