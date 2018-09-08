package com.wijjit.StellarUtils;


import com.wijjit.StellarUtils.StellarUtils;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.SubmitTransactionResponse;
import org.stellar.sdk.Network;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.SignerKey;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;

public class StellarSmartContracts {
    Server server = new Server("https://horizon-testnet.stellar.org");

    KeyPair source = KeyPair.fromSecretSeed("SBPXCN4IB6DH5IQUBDBDVEMFHCYF3WAEBZFXGFSCDE3VZ55GYIATFAND");
    // GAVFEQ7GX4XA4VO2DNIUX4SUVQ7RXP2RAHE7PTHG3SNNLBKHHG4DW3LJ
    // SBPXCN4IB6DH5IQUBDBDVEMFHCYF3WAEBZFXGFSCDE3VZ55GYIATFAND

    KeyPair additionalSignerKeyPair = KeyPair.fromSecretSeed("SBTP4L7G7SGVGXFH2BGFAKNI37EXBGEQVSNPFOOV2YHTTGN3S5GJEDQT");
    // GBHT4POWMW27CLLJYLK5HJSKIU46EFHYCITDBSDMOT537TX2WH4BICHK
    // SBTP4L7G7SGVGXFH2BGFAKNI37EXBGEQVSNPFOOV2YHTTGN3S5GJEDQT

    KeyPair masterSignerKeyPair = KeyPair.fromSecretSeed("SB37UOOMZOFIIM7JKJPVA63QDLPONRM4ZTFV4YQPRNT3ZCJ5WNMKBQNO");
    // GDU2BWQB73DH2JV6NLAYG46YSIZIXDCZNJQP5HKFFBY4LA4ZY6AQJPBC
    // SB37UOOMZOFIIM7JKJPVA63QDLPONRM4ZTFV4YQPRNT3ZCJ5WNMKBQNO

    KeyPair INVIssuerKeyPair = KeyPair.fromAccountId("GDU2BWQB73DH2JV6NLAYG46YSIZIXDCZNJQP5HKFFBY4LA4ZY6AQJPBC");
    // keypair for investor coin issuer - need to edit to production key value.

    KeyPair WJTIssuerKeyPair = KeyPair.fromAccountId("GDU2BWQB73DH2JV6NLAYG46YSIZIXDCZNJQP5HKFFBY4LA4ZY6AQJPBC");
    // keypair for WJT token issuer - need to edit to production key value.

    KeyPair projectSponsorKeyPair = KeyPair.random();
    // keypair for project sponsor. Same sponsor will be used for some N of projects... new sponsors will be created as needed.

    KeyPair escrowKeyPair = KeyPair.random();
    // keypair for escrow account. Need to create a new escrow account for each new project.

    private KeyPair channelToUse;
    private Asset nativeXLM = new AssetTypeNative();
    private Asset WJT = new AssetTypeCreditAlphaNum4("WJT", WJTIssuerKeyPair);
    private Asset INV = new AssetTypeCreditAlphaNum4("INV", INVIssuerKeyPair);
    private Asset newParticipationToken;

    public void init() {
        Network.useTestNetwork();
        StellarUtils stellarChannels = new StellarUtils();
        stellarChannels.init();
        stellarChannels.loadChannelKeypairs("wijjit_stellar_channel_accounts.csv");
        channelToUse = stellarChannels.getNextAvailableChannelKeypair();

    }

    public void createNewFundraisingCampaign (int fundraisingGoalInUSD, int fundraisingTimeInDays, int simplePartTokenTotalToSell) {
        // fundraisingGoalInUSD - ex: $10000
        // fundraisingTimeInDays - ex: 2 weeks = 14
        // simplePartTokenTotalToSell - ex: 1000 - TOTAL number of SIMPLE (ie. single tier reward) participation tokens to sell.
        // Note: the fundraisingGoalInUSD should equal (number of all tokens)*(value of each token) - the amount raised should be
        // the total of all tokens sold. If the fundraising goal is met before the campaign is over,
        // new tokens need to be minted.

        this.init();
        String newParticipationTokenCode = "TBD123"; //should be something like DB.project.getCode()
        newParticipationToken = new AssetTypeCreditAlphaNum12(newParticipationTokenCode, projectSponsorKeyPair);

    }

    private KeyPair createNewEscrowAccount () {
        return channelToUse;
    }

    private String establishTrust() {
        return "OK";
    }


}
