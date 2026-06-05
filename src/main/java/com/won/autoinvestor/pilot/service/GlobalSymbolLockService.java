package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.mapper.PilotMapper;
import org.springframework.stereotype.Service;

@Service
public class GlobalSymbolLockService {

    private final PilotMapper pilotMapper;

    public GlobalSymbolLockService(PilotMapper pilotMapper) {
        this.pilotMapper = pilotMapper;
    }

    public boolean isLocked(String symbol) {
        return pilotMapper.countGlobalHeldSymbol(symbol) > 0;
    }
}
