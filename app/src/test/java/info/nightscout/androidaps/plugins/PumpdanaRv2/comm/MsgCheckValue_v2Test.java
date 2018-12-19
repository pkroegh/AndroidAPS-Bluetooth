package info.nightscout.androidaps.plugins.PumpdanaRv2.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgCheckValue;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.comm.MsgCheckValue_v2;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.utils.SP;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, DanaRv2Plugin.class, DanaRPlugin.class, ConfigBuilderPlugin.class, CommandQueue.class})
public class MsgCheckValue_v2Test {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockBus();
        AAPSMocker.mockDanaRPlugin();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockCommandQueue();
        Treatment t = new Treatment();
        MsgCheckValue_v2 packet = new MsgCheckValue_v2();
        // test message decoding
        packet.handleMessage(createArray(34, (byte) 3));
        DanaRPump pump = DanaRPump.getInstance();
        assertEquals(DanaRPump.EXPORT_MODEL, pump.model);
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