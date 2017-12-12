package com.nubits.nubitsj.examples;

import com.nubits.nubitsj.core.NetworkParameters;
import com.nubits.nubitsj.core.Wallet;
import com.nubits.nubitsj.params.TestNet3Params;
import com.nubits.nubitsj.wallet.DeterministicSeed;
import com.google.common.base.Joiner;

/**
 * The following example shows you how to create a deterministic seed from a hierarchical deterministic wallet represented as a mnemonic code.
 * This seed can be used to fully restore your wallet. The RestoreFromSeed.java example shows how to load the wallet from this seed.
 * 
 * In Nubits Improvement Proposal (BIP) 39 and BIP 32 describe the details about hierarchical deterministic wallets and mnemonic sentences
 * https://github.com/nubits/bips/blob/master/bip-0039.mediawiki
 * https://github.com/nubits/bips/blob/master/bip-0032.mediawiki
 */
public class BackupToMnemonicSeed {

    public static void main(String[] args) {

        NetworkParameters params = TestNet3Params.get();
        Wallet wallet = new Wallet(params);

        DeterministicSeed seed = wallet.getKeyChainSeed();
        System.out.println("seed: " + seed.toString());

        System.out.println("creation time: " + seed.getCreationTimeSeconds());
        System.out.println("mnemonicCode: " + Joiner.on(" ").join(seed.getMnemonicCode()));
    }
}
