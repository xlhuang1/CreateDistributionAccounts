package com.wijjit.StellarUtils;

import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.Network;
import org.stellar.sdk.xdr.SignerKey;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;


public class StellarUtils {

    Server server = new Server("https://horizon-testnet.stellar.org");

    private KeyPair source = KeyPair.fromSecretSeed("SBPXCN4IB6DH5IQUBDBDVEMFHCYF3WAEBZFXGFSCDE3VZ55GYIATFAND");
    // GAVFEQ7GX4XA4VO2DNIUX4SUVQ7RXP2RAHE7PTHG3SNNLBKHHG4DW3LJ
    // SBPXCN4IB6DH5IQUBDBDVEMFHCYF3WAEBZFXGFSCDE3VZ55GYIATFAND

    private KeyPair additionalSignerKeyPair = KeyPair.fromSecretSeed("SBTP4L7G7SGVGXFH2BGFAKNI37EXBGEQVSNPFOOV2YHTTGN3S5GJEDQT");
    // GBHT4POWMW27CLLJYLK5HJSKIU46EFHYCITDBSDMOT537TX2WH4BICHK
    // SBTP4L7G7SGVGXFH2BGFAKNI37EXBGEQVSNPFOOV2YHTTGN3S5GJEDQT

    private KeyPair masterSignerKeyPair = KeyPair.fromSecretSeed("SB37UOOMZOFIIM7JKJPVA63QDLPONRM4ZTFV4YQPRNT3ZCJ5WNMKBQNO");
    // GDU2BWQB73DH2JV6NLAYG46YSIZIXDCZNJQP5HKFFBY4LA4ZY6AQJPBC
    // SB37UOOMZOFIIM7JKJPVA63QDLPONRM4ZTFV4YQPRNT3ZCJ5WNMKBQNO

    private KeyPair[] keyArray;
    private Operation[] createChannelOps;
    private Operation[] operationOnAllChannels;
    private static int currentChannelIndex;
    private static int nextChannelIndex;
    private int[] indexesForSplittingTransactionSigners = null;

    public void init() {
        Network.useTestNetwork();
        currentChannelIndex = 0;
        nextChannelIndex = 1;
    }

    public static void main(String[] args) {
        try {
            StellarUtils myUtils = new StellarUtils();
            myUtils.init();
            myUtils.run(args);
            // comment out myUtils.run(args); and use below to generate keys for prod
            //myUtils.runProd(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run(String[] args) throws Exception {
//        this.generateRandomKeypairs();
//        this.saveKeypairsToCSV(keyArray);

        this.loadChannelKeypairs("wijjit_stellar_channel_accounts.csv");
        this.testAllKeyPairs();


        //this.setSignersAndPermissionsRunOnce();
        operationOnAllChannels = this.generateSetOptionsSetThresholdsOps(0, 2, 2);
        SubmitTransactionResponse res = this.submitStellarChannelAccountsTransaction(operationOnAllChannels);
        if (res.isSuccess()) {
            System.out.println("it worked!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res.getHash());
            System.out.println("transaction XDR is : ");
            System.out.println(res.getEnvelopeXdr());
            System.out.println("result XDR is : ");
            System.out.println(res.getResultXdr());
        } else {
            System.out.println("failure");
        }

    }

    public void runProd(String[] args) throws Exception {
        this.generateRandomKeypairs();
        this.submitStellarChannelAccountsTransaction(createChannelOps, "not-signed-by-master");
        this.saveKeypairsToCSV(keyArray);
        this.testAllKeyPairs();
        this.setSignersAndPermissionsRunOnce();

    }

    public void setSignersAndPermissionsRunOnce() throws IOException {
        // generates the operations for adding master key, and then submits operations in batches of 20.
        System.out.println("Trying to add master key with weight 2");
        operationOnAllChannels = this.generateAddMasterSignerOps(masterSignerKeyPair);
        for (int i = 0; i < indexesForSplittingTransactionSigners.length; i++) {
            SubmitTransactionResponse res = this.submitMultipleTransactionsBy20s(operationOnAllChannels, indexesForSplittingTransactionSigners[i]);
            if (res.isSuccess()) {
                System.out.println("it worked!!!");
                System.out.println("transaction hash is : ");
                System.out.println(res.getHash());
                System.out.println("transaction XDR is : ");
                System.out.println(res.getEnvelopeXdr());
                System.out.println("result XDR is : ");
                System.out.println(res.getResultXdr());
            } else {
                System.out.println("failure");
            }
        }

        //adds additional signer's key to all channel accounts - no longer need to run submitMultipleTransactionsBy20s
        //because now we can sign everything using the master key.
        System.out.println("Trying to add additional signer with weight 1");
        operationOnAllChannels = this.generateAddAdditionalSignerOps(additionalSignerKeyPair);
        SubmitTransactionResponse res = this.submitStellarChannelAccountsTransaction(operationOnAllChannels);
        if (res.isSuccess()) {
            System.out.println("it worked!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res.getHash());
            System.out.println("transaction XDR is : ");
            System.out.println(res.getEnvelopeXdr());
            System.out.println("result XDR is : ");
            System.out.println(res.getResultXdr());
        } else {
            System.out.println("failure");
        }

        //sets threshold to 0,2,2 for all channels
        System.out.println("Trying to set thresholds");
        operationOnAllChannels = this.generateSetOptionsSetThresholdsOps(0, 2, 2);
        res = this.submitStellarChannelAccountsTransaction(operationOnAllChannels);
        if (res != null) {
            System.out.println("it worked!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res.getHash());
            System.out.println("transaction XDR is : ");
            System.out.println(res.getEnvelopeXdr());
            System.out.println("result XDR is : ");
            System.out.println(res.getResultXdr());
        } else {
            System.out.println("failure");
        }
    }

    public void generateRandomKeypairs() {
        // note: creates the operations needed to actually create the accounts.. but does not submit yet.
        // need to call this.submitStellarChannelAccountsTransaction(createChannelOps) in order to submit to Stellar network.
        keyArray = new KeyPair[100];
        createChannelOps = new Operation[100];

        System.out.println("Generating keypairs : ");

        for (int i = 0; i < 100; i++) {
            keyArray[i] = KeyPair.random();
            createChannelOps[i] = new CreateAccountOperation.Builder(keyArray[i], "40").build();
            System.out.println(keyArray[i].getAccountId());
            System.out.println(keyArray[i].getSecretSeed());
            System.out.println("--------");
        }
    }

    public void saveKeypairsToCSV(KeyPair[] keyArr) {
        if (keyArr == null) {
            System.out.println("error - no keys generated");
            return;
        }

        try {
            File myFile = new File("wijjit_stellar_channel_accounts-created.csv");
            if (myFile.createNewFile()){
                System.out.println("File created");
            } else {
                System.out.println("Warning - file "+myFile.getName()+" already exists. Overwriting file.");
            }

            FileWriter fileWriter = new FileWriter(myFile);
            BufferedWriter writer = new BufferedWriter(fileWriter);
            writer.write("_class,created,channelNum,privKey,pubKey\n");

            int lineCount = 1;
            while (lineCount <= keyArr.length) {
                String field1_class = "com.wijjit.profile.creator.models.StellarChannelAccounts";
                String field2_created = Instant.now().toString();
                String field3_channelNum = String.valueOf(lineCount);
                String field4_privKey = String.valueOf(keyArr[lineCount - 1].getSecretSeed());
                String field5_pubKey = String.valueOf(keyArr[lineCount - 1].getAccountId());
                if (lineCount == keyArr.length) {
                    writer.write(field1_class+","+field2_created+","+field3_channelNum+","+field4_privKey+","+field5_pubKey);
                    break;
                }
                writer.write(field1_class+","+field2_created+","+field3_channelNum+","+field4_privKey+","+field5_pubKey+"\n");
                lineCount++;
            }
            writer.close();
            System.out.println(keyArr.length+" Accounts written to file "+myFile.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadChannelKeypairs(String fileName) {

        ArrayList<String> secretKeyStringArrayList = new ArrayList<String>(100);
        keyArray = new KeyPair[100];

        try {
            File myFile = new File(fileName);
            FileReader fileReader = new FileReader(myFile);

            BufferedReader reader = new BufferedReader(fileReader);
            String line = null;

            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                String[] outArray = line.split(",", 5);
                secretKeyStringArrayList.add(outArray[3]);
                System.out.println(secretKeyStringArrayList.get(lineCount) + " added to ArrayList");
                lineCount++;
            }
            reader.close();

            if (!secretKeyStringArrayList.get(0).matches("^S")) {
                System.out.println("removing " + secretKeyStringArrayList.get(0));
                secretKeyStringArrayList.remove(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int i = 0;
        for (String S : secretKeyStringArrayList) {
            keyArray[i] = KeyPair.fromSecretSeed(S);
            i++;
        }

        System.out.println("keyArray " + keyArray.length + " keys loaded successfully.");

    }

    public KeyPair getCurrentChannelKeypair() {
        if (keyArray == null) {
            System.out.println("No Keys in keyArray - Initialize or Load Channel keys first.");
            return null;
        }
        System.out.println("Current Channel in use : " + currentChannelIndex);
        System.out.println(keyArray[currentChannelIndex].getAccountId());
        KeyPair currentChannelKeypair = keyArray[currentChannelIndex];
        return currentChannelKeypair;
    }

    public synchronized KeyPair getNextAvailableChannelKeypair() {
        if (keyArray == null) {
            System.out.println("No Keys in keyArray - Initialize or Load Channel keys first.");
            return null;
        }

        System.out.println("Current Channel in use : " + currentChannelIndex);
        System.out.println(keyArray[currentChannelIndex].getAccountId());
        System.out.println("Next Available Channel : " + nextChannelIndex);
        System.out.println(keyArray[nextChannelIndex].getAccountId());

        KeyPair nextChannelKeypair = keyArray[nextChannelIndex];
        System.out.println("Setting Channel to in use : " + nextChannelIndex);
        String res = this.setNextAvailableChannelKeypair();

        if (res == "Ok") {
            return nextChannelKeypair;
        } else {
            System.out.println("Critical Error: Setting Channel failed");
            return null;
        }

    }

    public synchronized String setNextAvailableChannelKeypair() {

        //Returns string "Ok" if successful

        currentChannelIndex = nextChannelIndex;
        nextChannelIndex = nextChannelIndex + 1;

        if (nextChannelIndex == keyArray.length) {
            System.out.println("End of keyArray reached - Resetting nextChannelIndex to 0");
            nextChannelIndex = 0;
        }

        return "Ok";

    }

    public KeyPair getMasterSignerKeyPair () {
        return masterSignerKeyPair;
    }

    public KeyPair getAdditionalSignerKeyPair () {
        return additionalSignerKeyPair;
    }


    public void testAllKeyPairs() {


        KeyPair myKey = null;
        for (int i = 0; i < keyArray.length + 2; i++) {
            //rotates through all keypairs and then tests wrap and ends on keypair 2.

            this.getCurrentChannelKeypair();
            myKey = this.getNextAvailableChannelKeypair();
        }

        System.out.println("finally done - last key is : " + myKey.getAccountId());
    }

    public Operation[] generatePaymentOperations() {
        Operation[] myTempOp = new Operation[keyArray.length];

        Asset XLM = new AssetTypeNative();
        for (int i = 0; i < keyArray.length; i++) {
            // operation.Builder(args) returns another builder object... so it is possible to chain objects.
            // need to call .build() to create a PaymentOperation Operation and then assign to op array to be later added to transaction in submitStellarChannelAccountsTransaction
            // note: for most Builder classes, you can run Operation.Builder(args).setSourceAccount(sourceKeyPair).build() to set the source account of the operation.
            // example: myTempOp[i] = new PaymentOperation.Builder(keyArray[i],XLM,"1").setSourceAccount(source).build();
            myTempOp[i] = new PaymentOperation.Builder(keyArray[i], XLM, ".002").build();

        }

        Operation[] paymentOnAllChannels = myTempOp;

        return paymentOnAllChannels;
    }

    public Operation[] generateAddMasterSignerOps(KeyPair masterKeyPair) {
        Operation[] addMasterOps = new Operation[keyArray.length];
        SignerKey masterSignerKey = new Signer().ed25519PublicKey(masterKeyPair);

        indexesForSplittingTransactionSigners = new int[(keyArray.length / 19) + 1];
        System.out.println("Transactions to be split into : " + indexesForSplittingTransactionSigners.length + " transactions due to signature limitations");
        int ind = 0;
        for (int x = 0; x < indexesForSplittingTransactionSigners.length; x++) {
            indexesForSplittingTransactionSigners[ind] = (x + 1) * 19 - 1;
            System.out.println("Splitting at key : " + indexesForSplittingTransactionSigners[ind]);
            ind++;
            if (ind == indexesForSplittingTransactionSigners.length) {
                // last one
                indexesForSplittingTransactionSigners[ind - 1] = indexesForSplittingTransactionSigners[ind - 2] + keyArray.length % 19;
            }
        }

        for (int i = 0; i < keyArray.length; i++) {
            addMasterOps[i] = new SetOptionsOperation.Builder()
                    .setSourceAccount(keyArray[i])
                    .setSigner(masterSignerKey, 2)
                    .build();
        }
        return addMasterOps;

    }

    public Operation[] generateAddAdditionalSignerOps(KeyPair additionalKeyPair) {
        Operation[] addAdditionalSignerOps = new Operation[keyArray.length];
        SignerKey addSignerKey = new Signer().ed25519PublicKey(additionalKeyPair);

        for (int i = 0; i < keyArray.length; i++) {
            addAdditionalSignerOps[i] = new SetOptionsOperation.Builder()
                    .setSourceAccount(keyArray[i])
                    .setSigner(addSignerKey, 1)
                    .build();
        }
        return addAdditionalSignerOps;
    }

    public Operation[] generateSetOptionsSetThresholdsOps(int lowThreshold, int mediumThreshold, int highThreshold) {
        Operation[] setThreshOps = new Operation[keyArray.length];
        System.out.println("Setting Low,Med,High Threshold to : " + lowThreshold + "," + mediumThreshold + "," + highThreshold);

        for (int i = 0; i < keyArray.length; i++) {
            setThreshOps[i] = new SetOptionsOperation.Builder()
                    .setSourceAccount(keyArray[i])
                    .setLowThreshold(lowThreshold)
                    .setMediumThreshold(mediumThreshold)
                    .setHighThreshold(highThreshold)
                    .build();
        }
        return setThreshOps;
    }


    public SubmitTransactionResponse submitStellarChannelAccountsTransaction(Operation[] opsArray, String ... argv) throws IOException {
        AccountResponse account = server.accounts().account(source);
        Transaction.Builder transaction = new Transaction.Builder(account); // account is the Source Account for the Transaction.
        // a sequence number will be used for account, and also the fee for the transaction will be charged from this account.

        if (opsArray.length > 100) {
            System.out.println("Number of Operations exceeds the maximum number of allowed operations in stellar (100)");
            return null;
        }

        int x = 0;
        while (x < keyArray.length) {
            // Operations is an array of built operations, and we cycle through the array to add all the operations to the transaction.
            transaction = transaction.addOperation(opsArray[x]);
            x++;
        }
        Transaction actualTransaction = transaction.build();
        actualTransaction.sign(source); //sign the transaction
        if (argv.length > 0) {
            if (argv[0] == "not-signed-by-master") {

            } else {
                actualTransaction.sign(masterSignerKeyPair); //sign with master key
            }
        } else {
            actualTransaction.sign(masterSignerKeyPair); //sign with master key - by default
        }

        try {
            SubmitTransactionResponse subResponse = server.submitTransaction(actualTransaction);
            System.out.println("submitStellarChannelAccounts - transaction submitted");
            System.out.println(subResponse.getEnvelopeXdr());
            System.out.println(subResponse.getHash());
            if (subResponse.isSuccess()) {
                System.out.println("success!!!");
                return subResponse;
            }
        } catch (Exception e) {
            System.out.println("Received Error! ");
            System.out.println(e.getMessage());
        }

        return null;
    }

    public SubmitTransactionResponse submitMultipleTransactionsBy20s(Operation[] opsArray, int endInd) throws IOException {
        // Use this function if need to split transactions into smaller transactions due to multiple signers.
        // Example: opsArray is an array of 100 ops that need to be signed by each corresponding keypair in the array.
        // However, Stellar limitation is one transaction can be signed by only up to 20 signers.
        // Therefore we divide the transaction into 100/19 = 5+1 transaction batches. (19 unique signers, 1 source signer for the transaction source)
        // endInd is the index where we will end each batch. For example, first batch will be transactions 1-19. second batch is 20-39. third batch is 40-59 etc.


        if (endInd - 18 < 0) {
            System.out.println("Error - start index will be negative");
            return null;
        }

        AccountResponse account = server.accounts().account(source);
        Transaction.Builder transaction = new Transaction.Builder(account); // account is the Source Account for the Transaction.

        int x = endInd - 18;
        while (x < endInd + 1) {

            // Operations is an array of built operations, and we cycle through the array to add all the operations to the transaction.
            transaction = transaction.addOperation(opsArray[x]);
            x++;
        }
        Transaction actualTransaction = transaction.build();
        actualTransaction.sign(source);

        //NOTE ONLY UP TO 20 SIGNATURES CAN BE ATTACHED TO A TRANSACTION
        x = endInd - 18;
        while (x < endInd + 1) {
            // Cycle through operations and attach signature of each secret key of keyArray[x]
            actualTransaction.sign(keyArray[x]);
            x++;
        }

        try {
            SubmitTransactionResponse subResponse = server.submitTransaction(actualTransaction);
            System.out.println("submitMultipleTransactionsBy20s - transaction submitted for index : " + endInd);
            System.out.println(subResponse.getEnvelopeXdr());
            System.out.println(subResponse.getHash());
            if (subResponse.isSuccess()) {
                System.out.println("success!!!");
                return subResponse;
            }
        } catch (Exception e) {
            System.out.println("Received Error! ");
            System.out.println(e.getMessage());
        }

        return null;

    }

}


