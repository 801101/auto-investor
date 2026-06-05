package com.won.autoinvestor.pilot.service;

import com.won.autoinvestor.pilot.domain.PilotMarketTick;

import java.util.List;

public interface PilotMarketDataClient {

    List<PilotMarketTick> pollLinkedMarketTicks();
}
