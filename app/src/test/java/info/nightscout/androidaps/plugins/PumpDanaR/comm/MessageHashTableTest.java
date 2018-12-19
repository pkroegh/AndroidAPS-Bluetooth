package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/31/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MessageHashTableTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        MessageHashTable messageHashTable = new MessageHashTable();
        MessageBase testMessage = messageHashTable.findMessage(0x41f2);
        assertEquals("CMD_HISTORY_ALL", testMessage.getMessageName());

    }


}