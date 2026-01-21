

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Convert;


public class Web3Withdraw {

	Logger logger = Logger.getLogger(this.getClass());
	    
	public String withdraw(String receiver, BigInteger amt) {
		String txid = null;
		try {
			Web3j web3 = Web3j.build(new HttpService("https://bsc-dataseed.binance.org/"));
	        Credentials credentials = Credentials.create("0x53e417ccf93c141e7fc7b6633717591efa0b1388c665ecee8a735724b9c22d82");

	        RawTransactionManager txManager =
	                new RawTransactionManager(web3, credentials, 56);
//	        RawTransactionManager txManager =
//	                new RawTransactionManager(web3, credentials, 97);

	        // USDT amount (example: 0.10 USDT)
	        BigInteger amount = amt; // 10 * 10^6

	        Function functionApprove = new Function(
	                "approve",
	                Arrays.asList(
	                        new Address("0xcc3FD5A36503A1135Cb9E692190F2cE9c4bAe401"),
	                        new Uint256(amount)
	                ),
	                Collections.emptyList()
	        );

	        String encodedFunction = FunctionEncoder.encode(functionApprove);
	        
	        Function functionWithdraw = new Function(
	                "withdraw",
	                Arrays.asList(
	                		new Uint256(amount),
	                        new Address(receiver)
	                ),
	                Collections.emptyList()
	        );

	        String withencodedFunction = FunctionEncoder.encode(functionWithdraw);

	        BigInteger bnbBalance = web3.ethGetBalance(
	                credentials.getAddress(),
	                DefaultBlockParameterName.LATEST
	        ).send().getBalance();

	        logger.info("BNB balance (wei): " + bnbBalance);
	        logger.info("BNB balance: " +
	                Convert.fromWei(bnbBalance.toString(), Convert.Unit.ETHER));
	        
	        EthGasPrice gasPriceResp = web3.ethGasPrice().send();
	        BigInteger gasPrice = gasPriceResp.getGasPrice();

	        logger.info("Gas Price: " + gasPrice + " wei");
	        
	        StaticGasProvider gasProvider = new StaticGasProvider(
	        		gasPrice, // 5 Gwei
	                BigInteger.valueOf(200_000)
	        );

	        EthSendTransaction tx = txManager.sendTransaction(
	                gasProvider.getGasPrice(),
	                gasProvider.getGasLimit(),
	                "0x55d398326f99059fF775485246999027B3197955",
	                encodedFunction,
	                BigInteger.ZERO
	        );

	        logger.info("TX HASH: " + tx.getTransactionHash());
	        if (tx.hasError()) {
	        	logger.info("RPC ERROR: " + tx.getError().getMessage());
	        }
	        EthSendTransaction txwith = txManager.sendTransaction(
	                gasProvider.getGasPrice(),
	                gasProvider.getGasLimit(),
	                "0xcc3FD5A36503A1135Cb9E692190F2cE9c4bAe401",
	                withencodedFunction,
	                BigInteger.ZERO
	        );
	        txid = txwith.getTransactionHash();
	        logger.info("TX HASH withdraw: " + txwith.getTransactionHash());
		} catch (Exception e) {
			// TODO: handle exception
		}
		return txid;
	}
	
	public Boolean checkTxStatus( String txHash) throws Exception {
		Web3j web3 = Web3j.build(new HttpService("https://bsc-dataseed.binance.org/"));
		
	    EthGetTransactionReceipt receiptResp =
	            web3.ethGetTransactionReceipt(txHash).send();

	    Optional<TransactionReceipt> receiptOpt = receiptResp.getTransactionReceipt();

	    if (!receiptOpt.isPresent()) {
	        return false;
	    }

	    TransactionReceipt receipt = receiptOpt.get();

	    if ("0x1".equals(receipt.getStatus())) {
	        return true;
	    }
	    return false;
	}
	
	public static void main(String[] args) throws Exception {
		Web3Withdraw web3Withdraw = new Web3Withdraw();
		System.out.println(web3Withdraw.checkTxStatus("0xbbfe04040a6599aa5066478970807b34e3680daed4ac87b43b7c5e927c4736ed"));
	}
}
