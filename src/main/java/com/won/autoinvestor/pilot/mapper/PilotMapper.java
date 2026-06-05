package com.won.autoinvestor.pilot.mapper;

import com.won.autoinvestor.pilot.domain.PilotMarketTick;
import com.won.autoinvestor.pilot.domain.PilotPosition;
import com.won.autoinvestor.pilot.domain.TradingLifecycleTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PilotMapper {

    List<PilotMarketTick> selectLatestMarketTicks();

    PilotPosition selectOpenPositionBySymbol(@Param("symbol") String symbol);

    int countGlobalHeldSymbol(@Param("symbol") String symbol);

    void insertOpenPosition(PilotPosition position);

    void insertPilotBalance(PilotPosition position);

    void touchOpenPosition(@Param("id") Long id, @Param("updatedAt") String updatedAt);

    void touchPilotBalance(@Param("symbol") String symbol, @Param("updatedAt") String updatedAt);

    void insertOrderIntent(@Param("symbol") String symbol,
                           @Param("marketCurrency") String marketCurrency,
                           @Param("side") String side,
                           @Param("orderAmount") String orderAmount,
                           @Param("orderQuantity") String orderQuantity,
                           @Param("referencePrice") String referencePrice,
                           @Param("reason") String reason,
                           @Param("status") String status,
                           @Param("createdAt") String createdAt);

    void insertObservation(@Param("symbol") String symbol,
                           @Param("marketCurrency") String marketCurrency,
                           @Param("marketState") String marketState,
                           @Param("entryPattern") String entryPattern,
                           @Param("positionStatus") String positionStatus,
                           @Param("survivalSeconds") long survivalSeconds,
                           @Param("observedAt") String observedAt);

    void insertMarketObservationData(@Param("symbol") String symbol,
                                     @Param("marketCurrency") String marketCurrency,
                                     @Param("marketState") String marketState,
                                     @Param("entryPattern") String entryPattern,
                                     @Param("positionStatus") String positionStatus,
                                     @Param("survivalSeconds") long survivalSeconds,
                                     @Param("grade") String grade,
                                     @Param("observedAt") String observedAt);

    String selectLatestAssetGrade(@Param("symbol") String symbol);

    void insertAssetGradeDecision(@Param("symbol") String symbol,
                                  @Param("grade") String grade,
                                  @Param("reason") String reason,
                                  @Param("decidedAt") String decidedAt);

    void insertAutobotBalance(@Param("symbol") String symbol,
                              @Param("marketCurrency") String marketCurrency,
                              @Param("quantity") String quantity,
                              @Param("averageEntryPrice") String averageEntryPrice,
                              @Param("grade") String grade,
                              @Param("createdAt") String createdAt);

    void insertAutobotOrderIntent(@Param("symbol") String symbol,
                                  @Param("marketCurrency") String marketCurrency,
                                  @Param("side") String side,
                                  @Param("shareQuantity") String shareQuantity,
                                  @Param("referencePrice") String referencePrice,
                                  @Param("assetGrade") String assetGrade,
                                  @Param("reason") String reason,
                                  @Param("status") String status,
                                  @Param("createdAt") String createdAt);

    void insertTradingHistoryMaster(@Param("systemType") String systemType,
                                    @Param("symbol") String symbol,
                                    @Param("marketCurrency") String marketCurrency,
                                    @Param("buyPrice") String buyPrice,
                                    @Param("buyQuantity") String buyQuantity,
                                    @Param("buyAmount") String buyAmount,
                                    @Param("buyTime") String buyTime,
                                    @Param("createdAt") String createdAt);

    void insertActiveStatusTracker(@Param("systemType") String systemType,
                                   @Param("masterId") Long masterId,
                                   @Param("symbol") String symbol,
                                   @Param("marketCurrency") String marketCurrency,
                                   @Param("entryPrice") String entryPrice,
                                   @Param("entryQuantity") String entryQuantity,
                                   @Param("entryAmount") String entryAmount,
                                   @Param("entryTime") String entryTime,
                                   @Param("createdAt") String createdAt);

    Long selectLatestOpenTradingHistoryMasterId(@Param("systemType") String systemType,
                                                @Param("symbol") String symbol,
                                                @Param("buyTime") String buyTime);

    List<TradingLifecycleTarget> selectLifecycleTargetsWithLatestTicks();

    List<TradingLifecycleTarget> selectBlackLifecycleTargetsWithLatestTicks();

    void updateActiveStatus(@Param("id") Long id,
                            @Param("status") String status,
                            @Param("statusEnteredAt") String statusEnteredAt,
                            @Param("grayEnteredAt") String grayEnteredAt,
                            @Param("forceLiquidationFlag") String forceLiquidationFlag,
                            @Param("updatedAt") String updatedAt);

    void markActiveStatusBadSector(@Param("id") Long id,
                                   @Param("updatedAt") String updatedAt,
                                   @Param("reason") String reason);

    void updateTradingHistorySell(@Param("systemType") String systemType,
                                  @Param("masterId") Long masterId,
                                  @Param("symbol") String symbol,
                                  @Param("sellPrice") String sellPrice,
                                  @Param("sellTime") String sellTime,
                                  @Param("pnlRatio") String pnlRatio,
                                  @Param("forceLiquidationFlag") String forceLiquidationFlag);

    void insertTradingStatusHistoryLog(@Param("trackerId") Long trackerId,
                                       @Param("masterId") Long masterId,
                                       @Param("systemType") String systemType,
                                       @Param("symbol") String symbol,
                                       @Param("previousStatus") String previousStatus,
                                       @Param("newStatus") String newStatus,
                                       @Param("eventType") String eventType,
                                       @Param("reason") String reason,
                                       @Param("forceLiquidationFlag") String forceLiquidationFlag,
                                       @Param("loggedAt") String loggedAt);

    void insertTrainingDatasetFromMaster(@Param("masterId") Long masterId,
                                         @Param("createdAt") String createdAt);

    void deleteActiveStatusTracker(@Param("id") Long id);

    void closePilotHolding(@Param("symbol") String symbol, @Param("closedAt") String closedAt);

    void closePilotBalance(@Param("symbol") String symbol, @Param("closedAt") String closedAt);

    void closeAutobotHolding(@Param("symbol") String symbol, @Param("closedAt") String closedAt);
}
