package info.nightscout.androidaps.plugins.pump.medtronicESP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import info.nightscout.androidaps.plugins.pump.medtronicESP.MedtronicPump;

class Encryption {
    /*

    private static Encryption instance = null;

    static final private List<Integer> prime = Arrays.asList(
            1073676287,
            1088888881,
            1095912793,
            1097393351,
            1097393447,
            1097393663
    );
    static final private List<List<Integer>> coprimes = Arrays.asList(
            Arrays.asList(
                    2, 3, 4, 5
            ),
            Arrays.asList(
                    2, 3, 4, 5
            ),
            Arrays.asList(
                    2, 3, 4, 5
            ),
            Arrays.asList(
                    2, 3, 4, 5
            ),
            Arrays.asList(
                    2, 3, 4, 5
            ),
            Arrays.asList(
                    2, 3, 4, 5
            )
    );

    static final private int minKeySize = 1000000000;
    static final private int maxKeySize = 2000000000;

    Encryption() {
        Random integer = new Random();
        primeIndex = integer.nextInt(prime.size());
        coprimeIndex = integer.nextInt(coprimes.size());
        privateKey = integer.nextInt((maxKeySize - minKeySize) + 1) + minKeySize;
        calculatePublicKey();
    }

    protected static Encryption getInstance(){
        if (instance == null) instance = new Encryption();
        return instance;
    }

    protected int getPrime() {
        return prime.get(primeIndex);
    }

    protected int getCoprime() {
        return coprimes.get(primeIndex).get(coprimeIndex);
    }

    private void calculatePublicKey() {
        APSPublicKey = (int)(Math.pow(getCoprime(),privateKey) % getPrime());
    }

    private void calculateSharedKey() {

    }

    protected void setESPPublicKey(int publicKey) {

    }

    protected int getEncrytionKey() {

    }

    private int primeIndex = 0;
    private int coprimeIndex = 0;
    private int privateKey = 0;
    private int APSPublicKey = 0;
    private int ESPPublicKey = 0;

    private int sharedKey = 0;
    */
}
