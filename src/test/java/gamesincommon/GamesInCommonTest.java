package gamesincommon;

import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

public class GamesInCommonTest {

    GamesInCommon gamesInCommon;

    @Before
    public void setUp() throws Exception {
        gamesInCommon = new GamesInCommon();
        gamesInCommon.requireWebCheck(false);
    }

    @Test
    public void testGetLogger() throws Exception {
        Logger loggerUnderTest = gamesInCommon.getLogger();
        assertNotNull(loggerUnderTest);
    }
}