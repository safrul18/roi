

import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import com.util.DBUtil;


public class FilterWihtdrawal {

	//private static String blockchainUrl = "https://bsc-dataseed3.binance.org/";
	//private static String contractAddress = "0x1FF9c8BC77C0A04e3b619DBE3299E0AB161CCD6F";
	//testnet
	private static String blockchainUrl = "https://data-seed-prebsc-2-s3.binance.org:8545/";
	private static String contractAddress = "0x2E5909914749f16a20fA89708b4E729E77FAfa7A";
	
	private static String receiveAddress = "0x00000000000000000000000033386c17bddee40938cf1a81d1cc697f54ef57c2";
	
	private static Logger logger = Logger.getLogger(FilterWihtdrawal.class);
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		logger.info("listener withdrawal blockchain running");
		Web3j web3j = Web3j.build(new HttpService(blockchainUrl));

		final Event WITHDRAWAL_EVENT = new Event("Withdrawal", 
		        Arrays.<TypeReference<?>>asList(
		          new TypeReference<Address>(true) {},
		          new TypeReference<Uint256>(true) {},
		          new TypeReference<Uint256>(true) {},
		          new TypeReference<Uint256>(true) {}));
		EthFilter filterWith = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, contractAddress);
		filterWith.addSingleTopic(EventEncoder.encode(WITHDRAWAL_EVENT));
		web3j.ethLogFlowable(filterWith).subscribe(event -> {
			logger.info(event.getTransactionHash());
			logger.info(">>>>>>>>>>>>>>>>>>>>>");
			logger.info(event.getTopics().get(1));
			String useridhex = event.getData().substring(2, 66);
			String amounthex = event.getData().substring(66, 130);
			BigInteger userid = new BigInteger(useridhex, 16);
			BigInteger amount = new BigInteger(amounthex, 16).divide(new BigInteger("1000000000000000000"));
			logger.info("userid:"+userid);
			logger.info(amount+":"+amount);
			//save to db
			try {
				Connection con = DBUtil.getConnection();
				
				String queryCheck = "select count(*) as total  from trans where txid=?";
				PreparedStatement pstmtquery = con.prepareStatement(queryCheck);
				pstmtquery.setString(1, event.getTransactionHash());
				
				ResultSet rsQuery = pstmtquery.executeQuery();
				int total = 0;
				if(rsQuery.next()) {
					total = rsQuery.getInt("total");
				}
				rsQuery.close();
				if(total == 0) {
					//insert to trans
					String queryInsert = "insert into trans set userid=?,amount=?,stat=5,createddate=now(),type=2,comment=?,fromaddr=?,paymentmethod=1";
					PreparedStatement pstmtInsert = con.prepareStatement(queryInsert);
					pstmtInsert.setLong(1, Long.parseLong(userid.toString()));
					pstmtInsert.setDouble(2, Double.parseDouble(amount.toString()));
					pstmtInsert.setString(3, event.getTransactionHash());
					pstmtInsert.setString(4, event.getTopics().get(1));
					pstmtInsert.execute();
					
					//update balance
					String queryUpBal = "update users set balance = balance - ? where user_id=?";
					PreparedStatement pstmtQueryUp = con.prepareStatement(queryUpBal);
					pstmtQueryUp.setDouble(1, Double.parseDouble(amount.toString()));
					pstmtQueryUp.setLong(2, Long.parseLong(userid.toString()));
					pstmtQueryUp.execute();
					
					//insert history balance
					String queryInBal = "insert into hist_bal set amount = -?,balance=(select balance from users where user_id=?),createddate=now(),user_id=?,trans_id=2";
					PreparedStatement pstmtQueryInBal = con.prepareStatement(queryInBal);
					pstmtQueryInBal.setDouble(1, Double.parseDouble(amount.toString()));
					pstmtQueryInBal.setLong(2, Long.parseLong(userid.toString()));
					pstmtQueryInBal.setLong(3, Long.parseLong(userid.toString()));
					pstmtQueryInBal.execute();
					
					con.commit();
				}
				rsQuery.close();
				pstmtquery.close();
				
			} catch (Exception e) {
				e.printStackTrace();
				logger.error(e.getMessage(), e);
			}
			
			
			
        });

		
	}
	
}
