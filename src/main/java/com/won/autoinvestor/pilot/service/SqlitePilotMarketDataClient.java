package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.domain.PilotMarketTick;
import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SqlitePilotMarketDataClient implements PilotMarketDataClient {

    private final PilotMapper pilotMapper;

    public SqlitePilotMarketDataClient(PilotMapper pilotMapper) {
        this.pilotMapper = pilotMapper;
    }

    @Override
    public List<PilotMarketTick> pollLinkedMarketTicks() {
        return pilotMapper.selectLatestMarketTicks();
    }
}
