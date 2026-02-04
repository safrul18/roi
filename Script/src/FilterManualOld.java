

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;

import com.util.DBPool;
import com.util.DBUtil;
import com.util.DerivedWallet;
import com.util.WalletService;

import io.reactivex.disposables.Disposable;


public class FilterManualOld {

	private static String blockchainUrl = "https://bsc-mainnet.core.chainstack.com/221c720cb0711d887ff7c345637783fc";
	private static String contractAddress = "0x55d398326f99059fF775485246999027B3197955";
	//testnet
//	private static String blockchainUrl = "https://data-seed-prebsc-2-s3.binance.org:8545/";
//	private static String contractAddress = "0x570EFFA77727A368c5353479f9b79Bfe6462357F";
	
	private static Logger logger = Logger.getLogger(FilterManualOld.class);
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		logger.info("listener deposit blockchain running");
		Web3j web3j = Web3j.build(new HttpService(blockchainUrl));
//		web3j.transactionFlowable().subscribe(tx -> {
//			//System.out.println(tx.getValueRaw()+">>>"+tx.getTo()+">>>>"+tx.getValue());
//		    if(tx.getTo().equals("0x516dC6912933bbef69317753d3B5dB01bb6F4688") ||tx.getTo().equals("0x227126c6cBb4A6a9a205995cd7b8236C66Eb199B")) {
//		    	System.out.println(tx.getTransactionIndexRaw()+">>>"+tx.getValueRaw()+">>>"+tx.getTo()+">>>>"+tx.getValue());
//		    }
//			
//		});
		//deposit
		EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
		BigInteger startBlock = blockNumber.getBlockNumber();

		DefaultBlockParameter start = new DefaultBlockParameterNumber(startBlock);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		while(true) {
			try {
				blockNumber = web3j.ethBlockNumber().send();
				BigInteger endBlock = blockNumber.getBlockNumber();
				
				DefaultBlockParameter end = new DefaultBlockParameterNumber(endBlock);
				logger.info("start : "+startBlock+" end : "+endBlock);
				final Event TRANSFER_EVENT = new Event("Transfer",
					    Arrays.<TypeReference<?>>asList(
					        new TypeReference<Address>(true) {},   // from
					        new TypeReference<Address>(true) {},   // to
					        new TypeReference<Uint256>() {}         // value (NOT indexed)
					    )
					);
				
				EthFilter filter = new EthFilter(start, end, contractAddress);
				filter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));
				Disposable subscription = web3j.ethLogFlowable(filter).subscribe(log  -> {
					EventValues eventValues = Contract.staticExtractEventParameters(TRANSFER_EVENT, log);
					if (eventValues == null) return;

				    String from = ((Address) eventValues.getIndexedValues().get(0))
				                    .getValue();

				    String to = ((Address) eventValues.getIndexedValues().get(1))
				                    .getValue();
				    
				    BigInteger value =
				            ((Uint256) eventValues.getNonIndexedValues().get(0))
				                .getValue();

				    BigDecimal amount =
				            new BigDecimal(value).divide(BigDecimal.TEN.pow(18)); // USDT
				    
					//save to db
					try (Connection con = DBPool.getConnection()){
						con.setAutoCommit(false);
						String queryAddress = "select user_id from users where address=?";
						PreparedStatement pstmtAddr = con.prepareStatement(queryAddress);
						pstmtAddr.setString(1, to);
						
						ResultSet rsAddr = pstmtAddr.executeQuery();
						if(rsAddr.next()) {
							logger.info(log.getTransactionHash());
						    logger.info("from>>>"+from);
						    logger.info("to>>>"+to);
						    logger.info(value);
						    logger.info(amount);
						    
							Long userId = rsAddr.getLong("user_id");
							String queryCheck = "select count(*) as total  from trans where txid=?";
							PreparedStatement pstmtquery = con.prepareStatement(queryCheck);
							pstmtquery.setString(1, log.getTransactionHash());
							
							ResultSet rsQuery = pstmtquery.executeQuery();
							int total = 0;
							if(rsQuery.next()) {
								total = rsQuery.getInt("total");
							}
							rsQuery.close();
							if(total == 0) {
								//insert to trans
								String queryInsert = "insert into trans set userid=?,amount=?,stat=5,createddate=now(),type=1,txid=?,fromaddr=?,paymentmethod=1";
								PreparedStatement pstmtInsert = con.prepareStatement(queryInsert);
								pstmtInsert.setLong(1, userId);
								pstmtInsert.setBigDecimal(2, amount);
								pstmtInsert.setString(3, log.getTransactionHash());
								pstmtInsert.setString(4, from);
								pstmtInsert.execute();
								
								//update balance
								String queryUpBal = "update users set balance = balance + ? where user_id=?";
								PreparedStatement pstmtQueryUp = con.prepareStatement(queryUpBal);
								pstmtQueryUp.setBigDecimal(1, amount);
								pstmtQueryUp.setLong(2, userId);
								pstmtQueryUp.execute();
								
								//update totaldepo upline
								String sqlUpline = "select refuser from users where user_id=?";
								PreparedStatement pstmtQueryUpline = con.prepareStatement(sqlUpline);
								pstmtQueryUpline.setLong(1, userId);
								
								String spcodeupline = null;
								ResultSet rsUpline= pstmtQueryUpline.executeQuery();
								if(rsUpline.next()) {
									spcodeupline = rsUpline.getString("refuser");
								}
								rsUpline.close();
								pstmtQueryUpline.close();
								while(spcodeupline!=null) {
									//update totaldepo
									String sqlUplineUpdate = "update users set totaldepo=totaldepo+? where sponsorcode=?";
									PreparedStatement pstmtQueryUplineUpdate = con.prepareStatement(sqlUplineUpdate);
									pstmtQueryUplineUpdate.setBigDecimal(1, amount);
									pstmtQueryUplineUpdate.setString(2, spcodeupline);
									pstmtQueryUplineUpdate.execute();
									
									String sqlUpline2 = "select refuser from users where sponsorcode=?";
									PreparedStatement pstmtQueryUpline2 = con.prepareStatement(sqlUpline2);
									pstmtQueryUpline2.setString(1, spcodeupline);
									
									ResultSet rsUpline2= pstmtQueryUpline2.executeQuery();
									if(rsUpline2.next()) {
										spcodeupline = rsUpline2.getString("refuser");
									}else {
										spcodeupline = null;
									}
									rsUpline2.close();
									pstmtQueryUpline2.close();
								}
								
								//insert history balance
								String queryInBal = "insert into hist_bal set amount = ?,balance=(select balance from users where user_id=?),createddate=now(),user_id=?,trans_id=1,detail_desc=?";
								PreparedStatement pstmtQueryInBal = con.prepareStatement(queryInBal);
								pstmtQueryInBal.setBigDecimal(1, amount);
								pstmtQueryInBal.setLong(2, userId);
								pstmtQueryInBal.setLong(3, userId);
								pstmtQueryInBal.setString(4, log.getTransactionHash());
								pstmtQueryInBal.execute();
								
								con.commit();
								
								//sweep usdt
								WalletService walletService = new WalletService();
								walletService.waitForConfirmations(log.getTransactionHash());
								DerivedWallet derivedWallet = walletService.deriveWallet(userId.intValue());
								walletService.sweepDeposit(to, amount, derivedWallet.getPrivateKey());
							}
							rsQuery.close();
							pstmtquery.close();
						}
						rsAddr.close();
						pstmtAddr.close();
						
						
					} catch (Exception e) {
						e.printStackTrace();
						logger.error(e.getMessage(), e);
					}
		        });
				try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				startBlock = endBlock;
				start = end;
				subscription.dispose();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
		
//		final Event WITHDRAWAL_EVENT = new Event("Withdrawal", 
//		        Arrays.<TypeReference<?>>asList(
//		          new TypeReference<Address>(true) {},
//		          new TypeReference<Uint256>(true) {},
//		          new TypeReference<Uint256>(true) {},
//		          new TypeReference<Uint256>(true) {}));
//		EthFilter filterWith = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, contractAddress);
//		filterWith.addSingleTopic(EventEncoder.encode(WITHDRAWAL_EVENT));
//		web3j.ethLogFlowable(filterWith).subscribe(event -> {
//			logger.info(event.getTransactionHash());
//			logger.info(">>>>>>>>>>>>>>>>>>>>>");
//			logger.info(event.getTopics().get(1));
//			String useridhex = event.getData().substring(2, 66);
//			String amounthex = event.getData().substring(66, 130);
//			BigInteger userid = new BigInteger(useridhex, 16);
//			BigInteger amount = new BigInteger(amounthex, 16).divide(new BigInteger("1000000000000000000"));
//			logger.info("userid:"+userid);
//			logger.info(amount+":"+amount);
//			//save to db
//			try {
//				Connection con = DBUtil.getConnection();
//				
//				String queryCheck = "select count(*) as total  from trans where txid=?";
//				PreparedStatement pstmtquery = con.prepareStatement(queryCheck);
//				pstmtquery.setString(1, event.getTransactionHash());
//				
//				ResultSet rsQuery = pstmtquery.executeQuery();
//				int total = 0;
//				if(rsQuery.next()) {
//					total = rsQuery.getInt("total");
//				}
//				rsQuery.close();
//				if(total == 0) {
//					//insert to trans
//					String queryInsert = "insert into trans set userid=?,amount=?,stat=5,createddate=now(),type=2,comment=?,fromaddr=?,paymentmethod=1";
//					PreparedStatement pstmtInsert = con.prepareStatement(queryInsert);
//					pstmtInsert.setLong(1, Long.parseLong(userid.toString()));
//					pstmtInsert.setDouble(2, Double.parseDouble(amount.toString()));
//					pstmtInsert.setString(3, event.getTransactionHash());
//					pstmtInsert.setString(4, event.getTopics().get(1));
//					pstmtInsert.execute();
//					
//					//update balance
//					String queryUpBal = "update users set balance = balance - ? where user_id=?";
//					PreparedStatement pstmtQueryUp = con.prepareStatement(queryUpBal);
//					pstmtQueryUp.setDouble(1, Double.parseDouble(amount.toString()));
//					pstmtQueryUp.setLong(2, Long.parseLong(userid.toString()));
//					pstmtQueryUp.execute();
//					
//					//insert history balance
//					String queryInBal = "insert into hist_bal set amount = -?,balance=(select balance from users where user_id=?),createddate=now(),user_id=?,trans_id=2";
//					PreparedStatement pstmtQueryInBal = con.prepareStatement(queryInBal);
//					pstmtQueryInBal.setDouble(1, Double.parseDouble(amount.toString()));
//					pstmtQueryInBal.setLong(2, Long.parseLong(userid.toString()));
//					pstmtQueryInBal.setLong(3, Long.parseLong(userid.toString()));
//					pstmtQueryInBal.execute();
//					
//					con.commit();
//				}
//				rsQuery.close();
//				pstmtquery.close();
//				
//			} catch (Exception e) {
//				e.printStackTrace();
//				logger.error(e.getMessage(), e);
//			}
//			
//			
//			
//        });

		
	}
	
}
