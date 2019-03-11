package diplomaticobjectmodel.logicaltree

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{LazyModule, ResourceBindingsMap, SimpleDevice}
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.rocket.{DCacheParams, Frontend, ScratchpadSlavePort}
import freechips.rocketchip.tile.{RocketTileParams, TileParams, XLen}


class RocketLogicalTree(
  device: SimpleDevice,
  tileParams: TileParams,
  rocketParams: RocketTileParams,
  frontEnd: Frontend,
  dtim_adapter: Option[ScratchpadSlavePort],
  XLen: Int) extends LogicalTree {

  def getOMDCacheFromBindings(dCacheParams: DCacheParams, resourceBindingsMap: ResourceBindingsMap): Option[OMDCache] = {
    val omDTIM: Option[OMDCache] = dtim_adapter.map(_.device.getMemory(dCacheParams, resourceBindingsMap))
    val omDCache: Option[OMDCache] = tileParams.dcache.filterNot(_.scratch.isDefined).map(OMCaches.dcache(_, None))

    require(!(omDTIM.isDefined && omDCache.isDefined))

    omDTIM.orElse(omDCache)
  }

  def getInterruptTargets(): Seq[OMInterruptTarget] = {
    Seq(OMInterruptTarget(
      hartId = rocketParams.hartId,
      modes = OMModes.getModes(rocketParams.core.useVM)
    ))
  }

  override def  getOMComponents(resourceBindingsMap: ResourceBindingsMap, components: Seq[OMComponent]): Seq[OMComponent] = {
    val coreParams = rocketParams.core

    val omICache: OMICache = frontEnd.getOMICache(resourceBindingsMap)

    val omDCache = rocketParams.dcache.flatMap{ getOMDCacheFromBindings(_, resourceBindingsMap)}

    Seq(OMRocketCore(
      isa = OMISA.rocketISA(coreParams, XLen),
      mulDiv =  coreParams.mulDiv.map{ md => OMMulDiv.makeOMI(md, XLen)},
      fpu = coreParams.fpu.map{f => OMFPU(fLen = f.fLen)},
      performanceMonitor = PerformanceMonitor.permon(coreParams),
      pmp = OMPMP.pmp(coreParams),
      documentationName = tileParams.name.getOrElse("rocket"),
      hartIds = Seq(tileParams.hartId),
      hasVectoredInterrupts = true,
      interruptLatency = 4,
      nLocalInterrupts = coreParams.nLocalInterrupts,
      nBreakpoints = coreParams.nBreakpoints,
      branchPredictor = rocketParams.btb.map(OMBTB.makeOMI),
      dcache = omDCache,
      icache = Some(omICache)
    ))
  }
}