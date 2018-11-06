package com.wijjit.StellarUtils;

import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.Network;
import org.stellar.sdk.xdr.SignerKey;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;


public class StellarUtils {

    Server server = new Server("https://horizon-testnet.stellar.org");

    KeyPair source = KeyPair.fromSecretSeed("SBDYMLXDUGT2LYTFIRYUV74WZMAGUT7OT2FIQELWGDXUN3WHDLXVQU47");
    // GCJRHOQOIGPLAEFCDT4GWBEFYVFBKA4OJMRD6GNMRTCQC6K63EZVP3VC
    // SBDYMLXDUGT2LYTFIRYUV74WZMAGUT7OT2FIQELWGDXUN3WHDLXVQU47

    KeyPair additionalSignerKeyPair = KeyPair.fromSecretSeed("SBIMBTG2EAM4HGALZTMZ5SPPHSTERTVOWAO2QPBANYMR6F4CPUIR3AZY");
    // GDCWETEP4XVGVXUW5AYZXVI3YU7YNWQE6DI7LNHONLVRWW5VSG6GCWQR
    // SBIMBTG2EAM4HGALZTMZ5SPPHSTERTVOWAO2QPBANYMR6F4CPUIR3AZY

    KeyPair masterSignerKeyPair = KeyPair.fromSecretSeed("SBVGY64WHDDB4HXES4PPBGH7OWGNRKMJQUMY4RVORBXFS3STIYO7SRME");
    // GACKWIZJ4EMAOUNXVEYRXQTUYMKYNRFRQOSKCSNOXKTKFI5O7UARMNFV
    // SBVGY64WHDDB4HXES4PPBGH7OWGNRKMJQUMY4RVORBXFS3STIYO7SRME

    KeyPair invIssuerKeyPair = KeyPair.fromSecretSeed("SD32NTJ6M6DQDMVVCW6VK4NHHBV32O4TW7DWL44JB2FXGDEU5FP3CJBR");
    // Issuer for Investor Coins
    // GCS2TX6DINIJKKS2QX73QJOEVQQWTGMT3NLTAKL6JACJAXVZ3GNIUW22
    // SD32NTJ6M6DQDMVVCW6VK4NHHBV32O4TW7DWL44JB2FXGDEU5FP3CJBR

    KeyPair wjtIssuerKeyPair = KeyPair.fromSecretSeed("SAS7HPQ23J6BCXR65VVQLR3VVYA3EHMTFIRFUEI7EAUXNJNFONEDQ42F");
    // Issuer for WJT
    // GBGSZTYDFUOPPESLOX6RW5H6NJ5KNOSKTIG4VEH7H5RD325JSOA2W75T
    // SAS7HPQ23J6BCXR65VVQLR3VVYA3EHMTFIRFUEI7EAUXNJNFONEDQ42F

    private KeyPair[] keyArray; //array of keys that needs to either be generated via generateRandomKeypairs or loaded via loadChannelKeypairs
    private Operation[] createChannelOps;
    private Operation[] operationOnAllChannels;
    private static int currentChannelIndex;
    private static int nextChannelIndex;
    private int[] indexesForSplittingTransactionSigners = null;

    public void init() {
        Network.useTestNetwork();
        currentChannelIndex = 1;
        nextChannelIndex = 2;
    }

    public static void main(String[] args) {
        try {
            StellarUtils myUtils = new StellarUtils();
            myUtils.init();

            myUtils.run(args);
            // comment out myUtils.run(args); and use myUtils.runProd(args) to generate keys for prod

            //myUtils.runProd(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run(String[] args) throws Exception {
        this.loadChannelKeypairs("wijjit_stellar_channel_accounts-new.csv");
        this.establishTrustlinesRunOnce();

    }

    public void run1(String[] args) throws Exception {
        this.generateRandomKeypairs(200);
        this.saveKeypairsToCSV(keyArray, "wijjit_stellar_channel_accounts-new.csv");

        this.loadChannelKeypairs("wijjit_stellar_channel_accounts-new.csv");
        this.testAllKeyPairs();

        SubmitTransactionResponse res = null;
        if (createChannelOps.length > 100) {
            ArrayList<Operation[]> newArray = splitOperationsIntoArrayList(createChannelOps, 100, createChannelOps.length);
            for (int i = 0; i < newArray.size(); i++) {
                System.out.println("submitting transaction batch " + i + ".");
                res = this.submitStellarChannelAccountsTransaction(newArray.get(i), "not-signed-by-master");
            }
        } else {
            res = this.submitStellarChannelAccountsTransaction(createChannelOps, "not-signed-by-master");
        }
        this.run2(args);
    }

    public void run2(String[] args) throws Exception {
        this.setSignersAndPermissionsRunOnce();
        this.establishTrustlinesRunOnce();
    }

    public void runProd(String[] args) throws Exception {
        /*
        this.generateRandomKeypairs();
        SubmitTransactionResponse res = this.submitStellarChannelAccountsTransaction(createChannelOps, "not-signed-by-master");
        if (res == null) {
            System.out.println("woops");
            return;
        }
        this.saveKeypairsToCSV(keyArray, "wijjit_stellar_channel_accounts-new.csv");
        this.testAllKeyPairs();
        this.setSignersAndPermissionsRunOnce();
        */

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

        SubmitTransactionResponse res = null;
        if (operationOnAllChannels.length > 100) {
            System.out.println("Warning: operationOnAllChannels.length : "+operationOnAllChannels.length+" exceeds " +
                    "the maximum number of operations allowed in a single transaction. Will need to split into multiple transactions");

            ArrayList<Operation[]> newArray = splitOperationsIntoArrayList(operationOnAllChannels, 100, operationOnAllChannels.length);
            for (int i = 0; i < newArray.size(); i++) {
                System.out.println("submitting transaction batch " + i + ".");
                res = this.submitStellarChannelAccountsTransaction(newArray.get(i));
            }
        } else {
            res = this.submitStellarChannelAccountsTransaction(operationOnAllChannels);
        }

        if (res.isSuccess()) {
            System.out.println("adding additional signer worked!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res.getHash());
        } else {
            System.out.println("failure");
        }

        //sets threshold to 0,2,2 for all channels
        System.out.println("Trying to set thresholds");
        operationOnAllChannels = this.generateSetOptionsSetThresholdsOps(0, 2, 2);
        SubmitTransactionResponse res2 = null;

        if (operationOnAllChannels.length > 100) {
            System.out.println("Warning: operationOnAllChannels.length : "+operationOnAllChannels.length+" exceeds " +
                    "the maximum number of operations allowed in a single transaction. Will need to split into multiple transactions");

            ArrayList<Operation[]> newArray = splitOperationsIntoArrayList(operationOnAllChannels, 100, operationOnAllChannels.length);
            for (int i = 0; i < newArray.size(); i++) {
                System.out.println("submitting transaction batch " + i + ".");
                res2 = this.submitStellarChannelAccountsTransaction(newArray.get(i));
            }
        } else {
            res2 = this.submitStellarChannelAccountsTransaction(operationOnAllChannels);
        }

        if (res2 != null) {
            System.out.println("Setting thresholds worked!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res2.getHash());
            //System.out.println("transaction XDR is : ");
            //System.out.println(res2.getEnvelopeXdr());
            //System.out.println("result XDR is : ");
            //System.out.println(res2.getResultXdr());
        } else {
            System.out.println("failure");
        }
    }

    public void establishTrustlinesRunOnce() throws IOException {
        // generates operations for creating trustlines with WJT, INV, and allowing trust with INV

        System.out.println("Attempting to establish trustline for WJT");
        Operation [] opsToRun = this.generateCreateOrChangeTrustlineOps(keyArray, wjtIssuerKeyPair, "WJT", "10000000000");
        SubmitTransactionResponse res = null;
        if (opsToRun.length > 100) {
            ArrayList<Operation[]> newArray = splitOperationsIntoArrayList(opsToRun, 100, opsToRun.length);
            for (int i = 0; i < newArray.size(); i++) {
                System.out.println("submitting transaction batch " + i + ".");
                res = this.submitStellarChannelAccountsTransaction(newArray.get(i));
            }
        } else {
            res = this.submitStellarChannelAccountsTransaction(opsToRun);
        }

        if (res.isSuccess()) {
            System.out.println("established Trustline for WJT!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res.getHash());
        } else {
            System.out.println("failure");
        }

        System.out.println("Attempting to establish trustline for INV");
        Operation [] opsToRun2 = this.generateCreateOrChangeTrustlineOps(keyArray, invIssuerKeyPair, "INV", "1000000");
        SubmitTransactionResponse res2 = null;
        if (opsToRun2.length > 100) {
            ArrayList<Operation[]> newArray = splitOperationsIntoArrayList(opsToRun2, 100, opsToRun2.length);
            for (int i = 0; i < newArray.size(); i++) {
                System.out.println("submitting transaction batch " + i + ".");
                res2 = this.submitStellarChannelAccountsTransaction(newArray.get(i));
            }
        } else {
            res2 = this.submitStellarChannelAccountsTransaction(opsToRun2);
        }

        if (res2.isSuccess()) {
            System.out.println("established Trustline for INV!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res2.getHash());
        } else {
            System.out.println("failure");
        }

        System.out.println("Attempting to Allow Trust from INV Issuer to all accounts in keyArray");
        Operation [] opsToRun3 = this.generateAllowTrustToAssetOps(keyArray, invIssuerKeyPair, "INV", true);
        SubmitTransactionResponse res3 = null;
        if (opsToRun3.length > 100) {
            ArrayList<Operation[]> newArray = splitOperationsIntoArrayList(opsToRun3, 100, opsToRun3.length);
            for (int i = 0; i < newArray.size(); i++) {
                System.out.println("submitting transaction batch " + i + ".");
                res3 = this.submitStellarChannelAccountsTransaction(newArray.get(i),"signed-by-invIssuerKeyPair");
            }
        } else {
            res3 = this.submitStellarChannelAccountsTransaction(opsToRun3, "signed-by-invIssuerKeyPair");
        }

        if (res3.isSuccess()) {
            System.out.println("Allowed Trust for INV!!!");
            System.out.println("transaction hash is : ");
            System.out.println(res3.getHash());
        } else {
            System.out.println("failure");
        }
    }

    public void generateRandomKeypairs(int n) {
        // note: creates the operations needed to actually create the accounts.. but does not submit yet.
        // need to call this.submitStellarChannelAccountsTransaction(createChannelOps) in order to submit to Stellar network.
        keyArray = new KeyPair[n];
        createChannelOps = new Operation[n];

        System.out.println("Generating keypairs : ");

        for (int i = 0; i < n; i++) {
            keyArray[i] = KeyPair.random();
            createChannelOps[i] = new CreateAccountOperation.Builder(keyArray[i], "5").build();
            System.out.println(keyArray[i].getAccountId());
            System.out.println(keyArray[i].getSecretSeed());
            System.out.println("--------");
        }
    }

    public void saveKeypairsToCSV(KeyPair[] keyArr, String fileName) {
        if (keyArr == null) {
            System.out.println("error - no keys generated");
            return;
        }

        try {
            File myFile = new File(fileName);
            if (myFile.createNewFile()){
                System.out.println("File created");
            } else {
                System.out.println("Warning - file "+myFile.getName()+" already exists. Overwriting file.");
            }

            FileWriter fileWriter = new FileWriter(myFile);
            BufferedWriter writer = new BufferedWriter(fileWriter);
            writer.write("_class,created,channelNum,privKey,pubKey\n");
            // note: this is column format for the csv file, to be later imported to MongoDB
            // can change the columns if necessary.

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
        // loads fileName which should be a csv file generated via saveKeypairsToCSV

        ArrayList<String> secretKeyStringArrayList = new ArrayList<String>(100);

        try {
            File myFile = new File(fileName);
            FileReader fileReader = new FileReader(myFile);

            BufferedReader reader = new BufferedReader(fileReader);
            String line = null;

            int lineCount = 0;            while ((line = reader.readLine()) != null) {
                String[] outArray = line.split(",", 5);
                secretKeyStringArrayList.add(outArray[3]);
                System.out.println(secretKeyStringArrayList.get(lineCount) + " added to ArrayList");
                lineCount++;
            }
            System.out.println("read in "+lineCount + " lines");
            reader.close();

            if (!secretKeyStringArrayList.get(0).matches("^S")) {
                System.out.println("removing " + secretKeyStringArrayList.get(0));
                secretKeyStringArrayList.remove(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        keyArray = new KeyPair[secretKeyStringArrayList.size()];

        int i = 0;
        for (String S : secretKeyStringArrayList) {
            keyArray[i] = KeyPair.fromSecretSeed(S);
            i++;
        }

        System.out.println("keyArray " + keyArray.length + " keys loaded successfully.");

    }

    // =================================================================================
    // Channels stuff

    public KeyPair getCurrentChannelKeypair() {
        //note: currentChannelIndex - starts at 1, goes to n (i.e. 1-100 for 100 channels).
        //note: keyArray - is an array and obviously the index goes from 0-99 if there are 100 keys...

        if (keyArray == null) {
            System.out.println("No Keys in keyArray - Initialize or Load Channel keys first.");
            return null;
        } else if (currentChannelIndex - 1 < 0) {
            System.out.println("Error - currentChannelIndex [" + currentChannelIndex + "] is out of bounds");
            return null;
        }

        System.out.println("Current Channel in use : " + currentChannelIndex);
        System.out.println(keyArray[currentChannelIndex-1].getAccountId());
        KeyPair currentChannelKeypair = keyArray[currentChannelIndex-1];
        return currentChannelKeypair;
    }

    public synchronized KeyPair getNextAvailableChannelKeypair() {
        if (keyArray == null) {
            System.out.println("No Keys in keyArray - Initialize or Load Channel keys first.");
            return null;
        } else if ((currentChannelIndex - 1 < 0) || (nextChannelIndex - 1 < 0)) {
            System.out.println("Error - currentChannelIndex or nextChannelIndex is out of bounds");
            return null;
        }

//        System.out.println("Current Channel in use : " + currentChannelIndex);
//        System.out.println(keyArray[currentChannelIndex-1].getAccountId());
        System.out.println("getNextAvailableChannelKeypair: Next Available Channel : " + nextChannelIndex);
        System.out.println(keyArray[nextChannelIndex-1].getAccountId());

        KeyPair nextChannelKeypair = keyArray[nextChannelIndex-1];
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

        currentChannelIndex = nextChannelIndex;
        nextChannelIndex = nextChannelIndex + 1;

        if (nextChannelIndex == keyArray.length + 1) {
            System.out.println("End of keyArray reached - Resetting nextChannelIndex to 1");
            nextChannelIndex = 1;
        }

        return "Ok";

    }

    public void testAllKeyPairs() {


        KeyPair myKey = null;
        for (int i = 0; i < keyArray.length + 2; i++) {
            //rotates through all keypairs and then tests wrap and ends on keypair 3.

            this.getCurrentChannelKeypair();
            myKey = this.getNextAvailableChannelKeypair();
        }

        System.out.println("finally done - last key is : " + myKey.getAccountId());
    }

    // =================================================================================
    // Generate Operations section

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
        // the index numbers where the transactions will be split.. i.e. for 100 operations, indexes will be 19, 38, 57, etc...
        // note: this needs to be done because there can be a max of 20 signatures per transaction, so if each account needs to sign an operation, then we can only submit
        // transactions that contain up to 19 unique individual accounts.
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

    public Operation[] generateCreateOrChangeTrustlineOps(KeyPair[] inputKeyArray, KeyPair assetIssuerKeyPair, String assetName, String assetLimit) throws IOException {
        // creates trustline for new non-native asset for each keypair in inputKeyArray

        Operation[] createTrustlineOps = new Operation[inputKeyArray.length];
        Asset asset = Asset.createNonNativeAsset(assetName, assetIssuerKeyPair);
        System.out.println("Creating trustline for asset: "+assetName+" issued by: "+assetIssuerKeyPair.getAccountId());

        for (int i = 0; i < inputKeyArray.length; i++) {
            createTrustlineOps[i] = new ChangeTrustOperation.Builder(asset, assetLimit)
                    .setSourceAccount(inputKeyArray[i])
                    .build();
        }
        return createTrustlineOps;
    }

    public Operation[] generateAllowTrustToAssetOps(KeyPair[] inputKeyArray, KeyPair assetIssuerKeyPair, String assetName, boolean authorize) throws IOException {
        // creates allow trust operations to asset for each keypair in inputKeyArray.
        // todo - maybe add checks for assetName and assetIssuerKeyPair?

        Operation[] createAllowTrustOps = new Operation[inputKeyArray.length];
        System.out.println("Authorize t/f ["+authorize+"] from issuer "+assetIssuerKeyPair.getAccountId()+" and asset "+assetName+" to trustors");

        for (int i = 0; i < inputKeyArray.length; i++) {
            createAllowTrustOps[i] = new AllowTrustOperation.Builder(inputKeyArray[i], assetName, authorize)
                    .setSourceAccount(invIssuerKeyPair)
                    .build();
        }
        return createAllowTrustOps;
    }

    // =================================================================================
    // Submit Transactions Section

    public ArrayList<Operation[]> splitOperationsIntoArrayList(Operation[] opsArray, int chunkSize, int opsLength) {
        // Splits an Array of Operations (i.e. createChannelOps, operationOnAllChannels, etc.) into an ArrayList
        // which contains a list of operations arrays, with each array of size chunkSize, and with the last array of size
        // length % chunkSize - Example: opsArray is array of 220 operations. chunkSize is 100 - will split into ArrayList
        // with elements < [ops 0-99], [ops 100-199], [ops 200-220] >
        // Although opsLength can be calculated, I want it to be stated so that the method call will be explicit.

        if (opsArray.length != opsLength) {
            System.out.println("Error - input array and opsLength mismatch");
            return null;
        }

        int timesSplit = opsLength / chunkSize;
        int remainingOps = opsLength % chunkSize;
        if (remainingOps > 0) {
            timesSplit++;
            // need to split additional time to account for remainder of operations.
        }

        System.out.println("splitOperationsIntoArrayList: Splitting opsArray into "+ timesSplit + " transactions");
        int x = chunkSize;
        int n = 1; //initialize split counter

        ArrayList<Operation[]> returnArrayList = new ArrayList<>(timesSplit);

        for (int i = 0; i < timesSplit; i++) {
            if (n == timesSplit) {
                returnArrayList.add(Arrays.copyOfRange(opsArray, i*x, opsLength));
                break;
            }
            System.out.println("splitOperationsIntoArrayList: i is " + i + ", n is " + n + ", x is " + x);
            returnArrayList.add(Arrays.copyOfRange(opsArray, i*x,n*x));
            n++;
        }

        System.out.println("splitOperationsIntoArrayList: transaction split to n = " + n + " times");
        return returnArrayList;
    }


    public SubmitTransactionResponse submitStellarChannelAccountsTransaction(Operation[] opsArray, String ... argv) throws IOException {

        // if called with "not-signed-by-master", the transaction will not be signed by master signer. Otherwise will be signed by source account and master signer.

        AccountResponse account = server.accounts().account(source);
        Transaction.Builder transaction = new Transaction.Builder(account); // account is the Source Account for the Transaction.
        // a sequence number will be used for account, and also the fee for the transaction will be charged from this account.

        if (opsArray.length > 100) {
            System.out.println("Number of Operations exceeds the maximum number of allowed operations in stellar (100)");
            return null;
        }

        int x = 0;
        while (x < opsArray.length) {
            // Operations is an array of built operations, and we cycle through the array to add all the operations to the transaction.
            transaction = transaction.addOperation(opsArray[x]);
            x++;
        }
        Transaction actualTransaction = transaction.build();
        actualTransaction.sign(source); //sign the transaction
        if (argv.length > 0) {
            if (argv[0] == "not-signed-by-master") {

            } else if (argv[0] == "signed-by-invIssuerKeyPair") {
                System.out.println("signing with invIssuerKeyPair");
                actualTransaction.sign(invIssuerKeyPair);
            } else {
                actualTransaction.sign(masterSignerKeyPair); //sign with master key
            }
        } else {
            actualTransaction.sign(masterSignerKeyPair); //sign with master key - by default
        }

        try {
            SubmitTransactionResponse subResponse = server.submitTransaction(actualTransaction);
            System.out.println("submitStellarChannelAccounts - transaction submitted, xdr below");
            System.out.println(subResponse.getEnvelopeXdr());
            System.out.println("submitStellarChannelAccounts - transaction hash below");
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


