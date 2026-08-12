package com.won.autoinvestor.pilot;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface PilotMapper {

    String sumActiveHoldingQuantityByStockCode(Map<String, Object> parameter);

    String sumOpenBuyOrderQuantityByStockCode(Map<String, Object> parameter);

    String sumActiveInvestedAmountByStockCode(Map<String, Object> parameter);

    String sumOpenBuyOrderAmountByStockCode(Map<String, Object> parameter);

    int countActivePositions();

    int countActiveHeldAndOpenBuySlots();

    Long selectActivePositionIdByStockCode(Map<String, Object> parameter);

    Map<String, Object> selectActivePositionByStockCode(Map<String, Object> parameter);

    List<Map<String, Object>> selectActivePositions();

    List<Map<String, Object>> selectActiveBlackPositions();

    void insertSyncedPosition(Map<String, Object> parameter);

    void updateSyncedPosition(Map<String, Object> parameter);

    void updatePositionAfterEvaluation(Map<String, Object> parameter);

    void closePosition(Map<String, Object> parameter);

    int countOrderByIdempotencyKey(Map<String, Object> parameter);

    String selectOrderStatusByIdempotencyKey(Map<String, Object> parameter);

    List<Map<String, Object>> selectOpenBuyOrders();

    List<Map<String, Object>> selectOpenSellOrders();

    List<Map<String, Object>> selectOrdersForStatusSync();

    void updateOrderStatusById(Map<String, Object> parameter);

    void updateOrderBrokerResultByIdempotencyKey(Map<String, Object> parameter);

    void updateOrderStatusByBrokerOrderId(Map<String, Object> parameter);

    void updateOrderBrokerStatusById(Map<String, Object> parameter);

    int countOpenSellOrderByStockCode(Map<String, Object> parameter);

    void insertOrderRecordDetailed(Map<String, Object> parameter);

    void insertSchedulerExecution(Map<String, Object> parameter);

    void insertAuditLog(Map<String, Object> parameter);

    void insertTradeLifecycleHistory(Map<String, Object> parameter);

    int domesticCountMasterRows(Map<String, Object> parameter);

    int domesticCountBuyableCandidates(Map<String, Object> parameter);

    void domesticUpsertMaster(Map<String, Object> parameter);

    List<Map<String, Object>> domesticSelectCandidateEvaluations(Map<String, Object> parameter);

    void domesticUpsertDashboardRow(Map<String, Object> parameter);

    void domesticDeleteStaleDashboardRows(Map<String, Object> parameter);

    List<Map<String, Object>> domesticSelectDashboardRows(Map<String, Object> parameter);

    int domesticTouchCandidateSelected(Map<String, Object> parameter);

    void domesticUpdateCandidateBuyAttempt(Map<String, Object> parameter);

    void domesticMarkCandidateFailure(Map<String, Object> parameter);

    void domesticMarkCandidateSuccess(Map<String, Object> parameter);

    int domesticCountActiveHeldAndOpenBuyQuantity();

    int overseasCountMasterRows(Map<String, Object> parameter);

    int overseasCountBuyableCandidates(Map<String, Object> parameter);

    void overseasUpsertMaster(Map<String, Object> parameter);

    List<Map<String, Object>> overseasSelectCandidateEvaluations(Map<String, Object> parameter);

    void overseasUpsertDashboardRow(Map<String, Object> parameter);

    void overseasDeleteStaleDashboardRows(Map<String, Object> parameter);

    List<Map<String, Object>> overseasSelectDashboardRows(Map<String, Object> parameter);

    void overseasTouchCandidateSelected(Map<String, Object> parameter);

    void overseasUpdateCandidateBuyAttempt(Map<String, Object> parameter);

    void overseasMarkCandidateFailure(Map<String, Object> parameter);

    void overseasMarkCandidateSuccess(Map<String, Object> parameter);

    void overseasMarkFractionalVerificationAttempt(Map<String, Object> parameter);

    void overseasMarkFractionalVerificationYes(Map<String, Object> parameter);

    void overseasKeepFractionalVerificationUnknown(Map<String, Object> parameter);

    int overseasCountActiveHeldAndOpenBuyQuantity();
}
