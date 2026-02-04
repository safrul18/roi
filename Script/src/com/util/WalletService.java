package com.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;


public class WalletService {

	private static final String MNEMONIC = "conduct crumble shrimp plate firm slide photo key clump purpose ski notable";
	static final BigDecimal MIN_BNB_REQUIRED = new BigDecimal("0.0004");
	static final BigDecimal TOPUP_BNB_AMOUNT = new BigDecimal("0.001");
	static final BigInteger GAS_LIMIT_BNB = BigInteger.valueOf(21_000);
	static final BigInteger GAS_LIMIT_USDT =BigInteger.valueOf(100_000);
	static final BigInteger GAS_PRICE = Convert.toWei("3", Convert.Unit.GWEI).toBigInteger();
	private final String MAIN_PRIVATE_KEY = "efa271f484cbe886f23519638230811a3e5ec2cf86b5daf5a73a6391f9a43d29";
	private final String MAIN_WALLET_ADDRESS = "0xC3854ef1e4509eeE1Ac00317b2b64189Bd8B6C90";
	static final String USDT_BEP20 = "0x55d398326f99059fF775485246999027B3197955";
	static final Long chainId = 56L;
	static final int CONFIRMATIONS = 2;
	public static final String RPC = "https://bsc-mainnet.core.chainstack.com/221c720cb0711d887ff7c345637783fc";
	
	public DerivedWallet deriveWallet(int index,String mnemonic) {

        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        Bip32ECKeyPair masterKeypair = Bip32ECKeyPair.generateKeyPair(seed);

        int[] path = {
            44 | Bip32ECKeyPair.HARDENED_BIT,
            60 | Bip32ECKeyPair.HARDENED_BIT,
            0  | Bip32ECKeyPair.HARDENED_BIT,
            0,
            index
        };

        Bip32ECKeyPair childKeyPair =
                Bip32ECKeyPair.deriveKeyPair(masterKeypair, path);

        String address = "0x" + Keys.getAddress(childKeyPair.getPublicKey());
        String privateKey = childKeyPair.getPrivateKey().toString(16);

        return new DerivedWallet(index, address, privateKey);
    }
	
	public static BigInteger getUsdtBalance(String walletAddress) throws Exception {

	    // 🔹 Change RPC based on network
	    // Ethereum
	    // String rpcUrl = "https://mainnet.infura.io/v3/YOUR_KEY";

	    // BSC (recommended for BEP20 USDT)
	    String rpcUrl = "https://bsc-dataseed.binance.org/";

	    Web3j web3j = Web3j.build(new HttpService(rpcUrl));

	    // 🔹 USDT Contract Address
	    // Ethereum USDT
	    // String usdtContract = "0xdAC17F958D2ee523a2206206994597C13D831ec7";

	    // BSC USDT
	    String usdtContract = "0x55d398326f99059fF775485246999027B3197955";

	    Function function = new Function(
	            "balanceOf",
	            Arrays.asList(new Address(walletAddress)),
	            Collections.singletonList(new TypeReference<Uint256>() {})
	    );

	    String encodedFunction = FunctionEncoder.encode(function);

	    String value = web3j.ethCall(
	            Transaction.createEthCallTransaction(
	                    walletAddress,
	                    usdtContract,
	                    encodedFunction
	            ),
	            DefaultBlockParameterName.LATEST
	    ).send().getValue();

	    BigInteger rawBalance = (BigInteger)
	            FunctionReturnDecoder.decode(value, function.getOutputParameters())
	                    .get(0).getValue();

	    return rawBalance; // USDT has 6 decimals
	}
	
	public static String getUsdtBalanceReadable(String address) throws Exception {
	    BigInteger raw = getUsdtBalance(address);
	    return raw.divide(BigInteger.TEN.pow(18)).toString();
	}
	
	public BigDecimal getBnbBalance(String address) throws Exception {
		Web3j web3j = Web3j.build(new HttpService(RPC));
	    EthGetBalance balance =
	        web3j.ethGetBalance(
	            address,
	            DefaultBlockParameterName.LATEST
	        ).send();

	    return Convert.fromWei(
	        new BigDecimal(balance.getBalance()),
	        Convert.Unit.ETHER
	    );
	}

	
	public void ensureGasForSweep(String depositAddress) throws Exception {

	    BigDecimal balance = getBnbBalance(depositAddress);

	    if (balance.compareTo(MIN_BNB_REQUIRED) >= 0) {
	        return; // enough gas
	    }

	    System.out.println("⛽ Topping up gas for " + depositAddress);

	    String gasTx = sendGas(depositAddress, TOPUP_BNB_AMOUNT);

	    waitForConfirmations(gasTx);
	}
	
	public void waitForConfirmations(String txHash) throws Exception {
		Web3j web3j = Web3j.build(new HttpService(RPC));
	    while (true) {
	        EthGetTransactionReceipt receipt =
	            web3j.ethGetTransactionReceipt(txHash).send();

	        if (receipt.getTransactionReceipt().isPresent()) {
	            break;
	        }
	        Thread.sleep(3000);
	    }
	}

	
	public BigInteger getNonce(String address) throws Exception {
		Web3j web3j = Web3j.build(new HttpService(RPC));
	    EthGetTransactionCount ethGetTransactionCount =
	        web3j.ethGetTransactionCount(
	            address,
	            DefaultBlockParameterName.PENDING // IMPORTANT
	        ).send();

	    return ethGetTransactionCount.getTransactionCount();
	}
	
	public String sendGas(
	        String toAddress,
	        BigDecimal amountBnb
	) throws Exception {
		Web3j web3j = Web3j.build(new HttpService(RPC));
	    Credentials mainCred =
	        Credentials.create(MAIN_PRIVATE_KEY);

	    BigInteger valueWei =
	        Convert.toWei(amountBnb, Convert.Unit.ETHER)
	               .toBigInteger();

	    RawTransaction rawTx = RawTransaction.createEtherTransaction(
	        getNonce(mainCred.getAddress()),
	        GAS_PRICE,
	        GAS_LIMIT_BNB,
	        toAddress,
	        valueWei
	    );

	    byte[] signed =
	        TransactionEncoder.signMessage(rawTx, chainId, mainCred);

	    EthSendTransaction tx =
	        web3j.ethSendRawTransaction(
	            Numeric.toHexString(signed)
	        ).send();

	    if (tx.hasError()) {
	        throw new RuntimeException(tx.getError().getMessage());
	    }

	    return tx.getTransactionHash();
	}

	public String sweepUsdt(
	        String fromAddress,
	        String toAddress,
	        BigDecimal amount,
	        String privateKey
	) throws Exception {

	    Credentials credentials =
	        Credentials.create(privateKey);

	    BigInteger value =
	        amount.multiply(BigDecimal.TEN.pow(18)).toBigIntegerExact();

	    Function function = new Function(
	        "transfer",
	        Arrays.asList(
	            new Address(toAddress),
	            new Uint256(value)
	        ),
	        Arrays.<TypeReference<?>>asList(
	            new TypeReference<Bool>() {}
	        )
	    );

	    String encodedFunction =
	        FunctionEncoder.encode(function);

	    BigInteger nonce =
	        getNonce(credentials.getAddress());

	    RawTransaction rawTx =
	        RawTransaction.createTransaction(
	            nonce,
	            GAS_PRICE,
	            GAS_LIMIT_USDT,
	            USDT_BEP20,
	            encodedFunction
	        );

	    byte[] signedMessage =
	        TransactionEncoder.signMessage(
	            rawTx,
	            chainId,               // 🔥 MUST be correct (56 for BSC)
	            credentials
	        );

	    String hexTx =
	        Numeric.toHexString(signedMessage);
	    
	    Web3j web3j = Web3j.build(new HttpService(RPC));
	    EthSendTransaction response =
	        web3j.ethSendRawTransaction(hexTx).send();

	    if (response.hasError()) {
	        throw new RuntimeException(
	            "Sweep failed: " + response.getError().getMessage()
	        );
	    }

	    return response.getTransactionHash();
	}

	
	public void sweepDeposit(String address,BigDecimal amount,String privateKey) {
		try {
            ensureGasForSweep(address);

            String sweepTx = sweepUsdt(
            		address,
            		MAIN_WALLET_ADDRESS,
            		amount,
            		privateKey
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
	}
    
    public static void main(String[] args) throws Exception {
//    	for (int i = 0; i < 100; i++) {
//    		byte[] entropy = new byte[16]; // 128 bits = 12 words
//            SecureRandom random = new SecureRandom();
//            random.nextBytes(entropy);
//
//            String mnemonic = MnemonicUtils.generateMnemonic(entropy);
//            System.out.println("Seed Phrase:");
//            System.out.println(mnemonic);
//            
//        	WalletService walletService = new WalletService();
//        	DerivedWallet wallet = walletService.deriveWallet(0,mnemonic);
//        	System.out.println(wallet.getAddress());
//        	System.out.println(wallet.getPrivateKey());
//        	System.out.println(getUsdtBalanceReadable(wallet.getAddress()));
//        	BigDecimal amount = new BigDecimal(getUsdtBalanceReadable(wallet.getAddress()));
//        	if(amount != null && amount.signum() > 0) {
//        		walletService.sweepDeposit(wallet.getAddress(),amount,wallet.getPrivateKey());
//        	}
//		}
    	WalletService walletService = new WalletService();
    	DerivedWallet wallet = walletService.deriveWallet(1,WalletService.MNEMONIC);
    	System.out.println(wallet.getAddress());
    	System.out.println(wallet.getPrivateKey());
    	
	}
}