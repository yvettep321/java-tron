package org.tron.common.logsfilter.capsule;

import static org.tron.common.logsfilter.EventPluginLoader.matchFilter;
import static org.tron.protos.Protocol.Transaction.Contract.ContractType.CreateSmartContract;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;
import org.pf4j.util.StringUtils;
import org.tron.common.crypto.Hash;
import org.tron.common.logsfilter.ContractEventParserAbi;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.trigger.BlockContractLogTrigger;
import org.tron.common.logsfilter.trigger.ContractEventTrigger;
import org.tron.common.logsfilter.trigger.ContractLogTrigger;
import org.tron.common.logsfilter.trigger.ContractTrigger;
import org.tron.common.runtime.vm.DataWord;
import org.tron.common.runtime.vm.LogInfo;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.contract.SmartContractOuterClass.SmartContract.ABI;

public class BlockContractLogTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private BlockContractLogTrigger blockContractLogTrigger;


  public BlockContractLogTriggerCapsule(BlockCapsule block, long solidifiedNumber) {
    blockContractLogTrigger = new BlockContractLogTrigger();
    // get block info
    blockContractLogTrigger.setBlockHash(block.getBlockId().toString());
    blockContractLogTrigger.setTimeStamp(block.getTimeStamp());
    blockContractLogTrigger.setBlockNumber(block.getNum());
    blockContractLogTrigger.setLatestSolidifiedBlockNumber(solidifiedNumber);
    blockContractLogTrigger.setParentHash(block.getParentBlockId().toString());
    // bloom filter
//    BloomFilter<String> bloomFilterContractAndTopic = BloomFilter.create(Funnels.stringFunnel(
//        Charsets.UTF_8), 80, 0.01);
//    BloomFilter<String> bloomFilterContract = BloomFilter.create(Funnels.stringFunnel(
//        Charsets.UTF_8), 80, 0.01);
//    BloomFilter<String> bloomFilterTopic = BloomFilter.create(Funnels.stringFunnel(
//        Charsets.UTF_8), 80, 0.01);

    // get transaction info
    List<BlockContractLogTrigger.TransactionInBlock> transactionInBlockList = new ArrayList<>(block.getTransactions().size());
    blockContractLogTrigger.setTransactionList(transactionInBlockList);
    for (int i = 0; i < block.getTransactions().size(); i++) {
      TransactionCapsule trxCapsule = block.getTransactions().get(i);
      BlockContractLogTrigger.TransactionInBlock transactionInBlock = blockContractLogTrigger.new TransactionInBlock();
      List<ContractTrigger> contractTriggerCapsuleList = trxCapsule.getTrxTrace().getRuntimeResult().getTriggerList();
      if(contractTriggerCapsuleList.isEmpty()){
        continue;
      }
      if(ArrayUtils.contains(ArrayUtils.toArray(
          "2d8a0cfee773c3fc310cde6da911dde0544371b8c8c6a9d0f09bc1f584ae502e", // create pair
              "29f46feead32e839fbc72c8ab6cc968fc121733cde61f4c27c6ba4eccb66b2f5"), // for test
          trxCapsule.getTransactionId().toString())){
        continue;
      }
      ByteString contractAddress = ByteString.copyFrom(trxCapsule.getTrxTrace().getRuntimeResult().getContractAddress());
      if (contractAddress.size() > 0) {
          transactionInBlock
              .setContractAddress(StringUtil.encode58Check((contractAddress.toByteArray())));
      }
      transactionInBlock.setTransactionId(trxCapsule.getTransactionId().toString());
      transactionInBlock.setTransactionIndex(i);
      // log trigger
      List<ContractLogTrigger> logTriggers = new ArrayList<>(contractTriggerCapsuleList.size());
      transactionInBlock.setLogList(logTriggers);
      for (ContractTrigger trigger : contractTriggerCapsuleList) {
        // boomFilter
//        bloomFilterContract.put(trigger.getContractAddress());
//        if(!trigger.getLogInfo().getTopics().isEmpty()) {
//          bloomFilterTopic.put(trigger.getLogInfo().getTopics().get(0).toHexString());
//          bloomFilterContractAndTopic.put(
//              trigger.getContractAddress() + trigger.getLogInfo().getTopics().get(0).toHexString());
//        }
        List<String> filterNames = matchFilter(trigger);
        if (filterNames.isEmpty()) {
          continue;
        }
        trigger.setFilterNameList(filterNames);
        trigger.setBlockHash(blockContractLogTrigger.getBlockHash());
        trigger.setLatestSolidifiedBlockNumber(blockContractLogTrigger.getLatestSolidifiedBlockNumber());
        ContractLogTrigger contractLogTrigger = parseContractTriggerToLogTrigger(trigger);
        logTriggers.add(contractLogTrigger);
      }
      if(!logTriggers.isEmpty()){
        transactionInBlockList.add(transactionInBlock);
      }
    }
//    try {
//      ByteArrayOutputStream outStreamContract = new ByteArrayOutputStream();
//      ByteArrayOutputStream outStreamTopic = new ByteArrayOutputStream();
//      ByteArrayOutputStream outStreamContractAndTopic = new ByteArrayOutputStream();
//      bloomFilterContract.writeTo(outStreamContract);
//      bloomFilterTopic.writeTo(outStreamTopic);
//      bloomFilterContractAndTopic.writeTo(outStreamContractAndTopic);
//      blockContractLogTrigger.setBloomFilterContract(outStreamContract.toByteArray());
//      blockContractLogTrigger.setBloomFilterTopic(outStreamTopic.toByteArray());
//      blockContractLogTrigger.setBloomFilterContractAndTopic(outStreamContractAndTopic.toByteArray());
//    } catch (IOException ioE){
//    }
  }

  private ContractLogTrigger parseContractTriggerToLogTrigger(ContractTrigger contractTrigger) {
    ContractLogTrigger event = new ContractLogTrigger();
    event.setTriggerName(null);
    LogInfo logInfo = contractTrigger.getLogInfo();
    event.setTopicList(logInfo.getHexTopics());
    event.setData(logInfo.getHexData());
    RawData rawData = new RawData(logInfo.getAddress(), logInfo.getTopics(), logInfo.getData());
    event.setRawData(rawData);
    event.setLatestSolidifiedBlockNumber(contractTrigger.getLatestSolidifiedBlockNumber());
    event.setRemoved(contractTrigger.isRemoved());
    event.setUniqueId(contractTrigger.getUniqueId());
    event.setTransactionId(contractTrigger.getTransactionId());
    event.setContractAddress(contractTrigger.getContractAddress());
    event.setOriginAddress(contractTrigger.getOriginAddress());
    event.setCallerAddress("");
    event.setCreatorAddress(contractTrigger.getCreatorAddress());
    event.setBlockNumber(contractTrigger.getBlockNumber());
    event.setTimeStamp(contractTrigger.getTimeStamp());
    event.setBlockHash(contractTrigger.getBlockHash());
    event.setFilterNameList(contractTrigger.getFilterNameList());
    return event;
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postBlockContractLogTrigger(blockContractLogTrigger);
  }
}
