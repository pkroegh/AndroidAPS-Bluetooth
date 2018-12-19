package info.nightscout.androidaps.plugins.PumpdanaRv2.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MessageHashTable_v2;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgStatusAPS_v2;
import info.nightscout.utils.SP;

import static org.junit.Assert.*;
/**
 * Created by Rumen Georgiev on 30.10.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MessageHashTable_v2Test {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockBus();

        MessageHashTable_v2 packet = new MessageHashTable_v2();

        MessageBase forTesting = new MsgStatusAPS_v2();
        MessageBase testPacket = MessageHashTable_v2.findMessage(forTesting.getCommand());
        assertEquals(0xE001, testPacket.getCommand());
        // try putting another command
        MessageBase testMessage = new MessageBase();
        testMessage.SetCommand(0xE005);
        packet.put(testMessage);
        assertEquals(0xE005, packet.findMessage(0xE005).getCommand());
    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }

    double[] createArray(int length, double fillWith){
        double[] ret = new double[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }

}