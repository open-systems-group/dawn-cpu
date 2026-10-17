package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline
import chisel3.util.random.LFSR





class ICache(lineWidth: Int = 128) extends Module {
    val io = IO(new Bundle {
        val req = Input(new MemReq)
        val start = Input(Bool())
        val ready = Output(Bool())
        val done = Output(Bool())
        val miss = Output(Bool())
        val data = Output(UInt(32.W))

        val wb = Output(Bool())
        val wb_data = Output(UInt(lineWidth.W))
        val wb_addr = Output(UInt(32.W))

        val line_result = Input(UInt(lineWidth.W))
        val line_addr = Output(UInt(32.W))
        val line_valid = Input(Bool())

        
    })


    val CACHE_SETS = 256
    val LINE_WIDTH_WORDS = lineWidth/32
    val LOG_CACHE_SETS = log2Up(CACHE_SETS)
    val LOG_LINE_WIDTH_WORDS = log2Up(LINE_WIDTH_WORDS)
    val WAYS = 1
    val TAG_WIDTH = 32 - LOG_CACHE_SETS - LOG_LINE_WIDTH_WORDS - 2
    val LRU_WIDTH = 16
    val POLICYS = 2
    val LOG_POLICY_TEST_INTERVAL = 6
    val byte_offset = Wire(UInt(2.W))
    val cache_index = Wire(UInt(LOG_CACHE_SETS.W))
    val word_offset = Wire(UInt(LOG_LINE_WIDTH_WORDS.W))
    val cache_tag = Wire(UInt((32-LOG_CACHE_SETS-LOG_LINE_WIDTH_WORDS-2).W))
    val line_addr = Wire(UInt((32-LOG_LINE_WIDTH_WORDS-2).W))


    def getByteOffset(addr: UInt): UInt = addr(1, 0)

    def getWordOffset(addr: UInt): UInt = addr(LOG_LINE_WIDTH_WORDS + 1, 2)

    def getIndex(addr: UInt): UInt = addr(LOG_LINE_WIDTH_WORDS + LOG_CACHE_SETS + 1, LOG_LINE_WIDTH_WORDS + 2)

    def getTag(addr: UInt): UInt = addr(31, LOG_LINE_WIDTH_WORDS + LOG_CACHE_SETS + 2)

    def getLineAddr(addr: UInt): UInt = addr(31, LOG_LINE_WIDTH_WORDS + 2)

    



    val p_hit = Seq.fill(POLICYS)(RegInit(0.S(32.W)))
    val meta_array = Seq.fill(WAYS)(SyncReadMem(CACHE_SETS, new CacheMeta(TAG_WIDTH,LRU_WIDTH)))
    val data_array = Seq.fill(WAYS)(SyncReadMem(CACHE_SETS, UInt((32 * LINE_WIDTH_WORDS).W)))
    val state = RegInit(CacheState.IDLE)
    val current_mem_req = RegInit(0.U.asTypeOf(new MemReq))
    val fire = io.ready && io.start
    val lookup_address = Mux(fire, io.req.address, current_mem_req.address)
    // val lookup_address = Mux(state === CacheState.IDLE, io.req.address, current_mem_req.address)

    byte_offset := RegNext(getByteOffset(lookup_address))
    cache_index := (getIndex(lookup_address))
    word_offset := RegNext(getWordOffset(lookup_address))
    cache_tag := RegNext(getTag(lookup_address))
    line_addr := RegNext(getLineAddr(lookup_address))
    val read_enable = state === CacheState.IDLE || state === CacheState.LOOKUP

    val data_out_ways = VecInit(data_array.map(_.read(cache_index, read_enable)))
    val meta_out_ways = VecInit(meta_array.map(_.read(cache_index, read_enable)))

    val hit_ways = VecInit(meta_out_ways.map(m => m.valid && m.tag === cache_tag))
    val hit = hit_ways.asUInt.orR
    val hit_way  = if (WAYS == 1) 0.U else PriorityEncoder(hit_ways)

    val data_out = data_out_ways(hit_way)
    val meta_out = meta_out_ways(hit_way)

    val policy =  RegInit(0.U(log2Up(POLICYS).W))
    //NEED TO CHANGE IF MORE THAN 2 POLICY
    when(p_hit(1) > p_hit(0)) {
        policy :=1.U
    }.otherwise{
        policy:=0.U
    }

    val access_counter = RegInit(0.U(LRU_WIDTH.W))

    when(io.start) { access_counter := access_counter + 1.U }
    val lfsr = if (WAYS == 1) 0.U else LFSR(16)(log2Up(WAYS) - 1, 0)
    val victim_way_reg = RegInit(0.U(log2Up(WAYS).W))

    // that is awful
    val lru_way =
        if (WAYS == 1) 0.U
        else meta_out_ways.zipWithIndex
            .map { case (m, i) => (m.last_time, i.U(log2Up(WAYS).W)) }
            .reduce { (a, b) =>
                val a_older = a._1 <= b._1
                (Mux(a_older, a._1, b._1), Mux(a_older, a._2, b._2))
            }._2

    // cache_index is not registered here, so the profiler needs the index that produced hit
    val cache_index_reg = RegNext(cache_index)
    val policy_profiler =  VecInit(Seq.tabulate(POLICYS)(i => cache_index_reg(LOG_POLICY_TEST_INTERVAL - 1, 0) === i.U))

    val is_profiler = policy_profiler.asUInt.orR
    val profile_policy = PriorityEncoder(policy_profiler)

    val policy_victim = VecInit(lfsr, lru_way)                
    val active_policy = Mux(is_profiler, profile_policy, policy)

    val invalid_ways = VecInit(meta_out_ways.map(!_.valid))
    val victim_sel =
        if (WAYS == 1) 0.U
        else Mux(invalid_ways.asUInt.orR, PriorityEncoder(invalid_ways), policy_victim(active_policy))
    val victim_way = if (WAYS == 1) 0.U else Mux(state === CacheState.LOOKUP, victim_sel, victim_way_reg)
    val evict_meta = meta_out_ways(victim_way)
    val evict_data = data_out_ways(victim_way)

    val data_wr_en = WireDefault(VecInit(Seq.fill(WAYS)(false.B)))
    val data_wr_data = WireDefault(0.U((32 * LINE_WIDTH_WORDS).W))
    val meta_wr_en  = WireDefault(VecInit(Seq.fill(WAYS)(false.B)))
    val meta_wr_data = WireDefault(0.U.asTypeOf(new CacheMeta(TAG_WIDTH,LRU_WIDTH)))
    val write_addr = getIndex(current_mem_req.address)

    io.done := false.B
    io.miss := false.B
    io.data := 0.U

    io.ready:= state === CacheState.IDLE || (state === CacheState.LOOKUP && hit) // or hit
    io.wb := false.B
    io.wb_data := evict_data
    io.wb_addr := Cat(evict_meta.tag, cache_index, 0.U((LOG_LINE_WIDTH_WORDS + 2).W))
    io.line_addr := Cat(line_addr, 0.U((LOG_LINE_WIDTH_WORDS + 2).W))

    val words = VecInit(
    (0 until LINE_WIDTH_WORDS).map(i => data_out(32*i + 31, 32*i))
    )

    when(io.start && access_counter.andR){
        for(i <- 0 until POLICYS) {
            p_hit(i) := p_hit(i) >> 2
        }
    }.elsewhen(policy_profiler(0)){
        when(state === CacheState.LOOKUP) {
            when(hit) {
                p_hit(0) := p_hit(0) + 1.S
            }.otherwise{
                 p_hit(0) := p_hit(0) -1.S
            }
        }
    }.elsewhen(policy_profiler(1)){
        when(state === CacheState.LOOKUP) {
            when(hit) {
                p_hit(1) := p_hit(1) +1.S
            }.otherwise{
                p_hit(1) := p_hit(1) -1.S
            }
        }
    }

switch(state) {
    is(CacheState.IDLE) {
        when(io.start) {
            current_mem_req := io.req
            state := CacheState.LOOKUP
        }
    }

    is(CacheState.LOOKUP) {
        when(hit) { // hit

            

            when(current_mem_req.write) {
             
                io.done := true.B
            }.otherwise {
                io.done := true.B

                val word_data = words(word_offset)
                switch(current_mem_req.op){
                    is(MemOp.LW){
                        io.data := word_data
                    }
                    is(MemOp.LH){
                        when(byte_offset === 0.U){
                            io.data := ((word_data(15,0)).asSInt.pad(32)).asUInt
                        }.otherwise{
                            io.data := ((word_data(31,16)).asSInt.pad(32)).asUInt
                        }
                    }
                    is(MemOp.LB){
                        when(byte_offset === 0.U){
                            io.data := word_data(7,0).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 1.U){
                            io.data := word_data(15,8).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 2.U){
                            io.data := word_data(23,16).asSInt.pad(32).asUInt
                        }.otherwise{
                            io.data := word_data(31,24).asSInt.pad(32).asUInt
                        }
                    }
                }
            }
            when(io.start){
                current_mem_req := io.req
                state := CacheState.LOOKUP
            }.otherwise{
                state := CacheState.IDLE
            }

        }.otherwise { // miss
            victim_way_reg := victim_sel
            when(evict_meta.valid && evict_meta.dirty) { // valid + dirty → writeback first
                state := CacheState.WRITEBACK
            }.otherwise {             
                state := CacheState.MISS
            }
        }
    }

    is(CacheState.WRITEBACK) {
        io.wb := true.B
        state := CacheState.MISS
    }

    is(CacheState.MISS) {
        when(!io.line_valid) {
            io.miss := true.B
        }.otherwise {
            when(current_mem_req.write) {
               io.done := true.B
                  
            }.otherwise {
                data_wr_en(victim_way) := true.B
                data_wr_data := io.line_result
                meta_wr_en(victim_way) := true.B
                meta_wr_data.valid := true.B
                meta_wr_data.dirty := false.B//valid not dirty
                meta_wr_data.tag := getTag(current_mem_req.address)
                meta_wr_data.last_time := access_counter
                val lwords = VecInit((0 until LINE_WIDTH_WORDS).map(i => io.line_result(32*i + 31, 32*i)))
                val updated_word = lwords(word_offset)
                switch(current_mem_req.op){
                    is(MemOp.LW){
                        io.data := updated_word
                    }
                    is(MemOp.LH){
                        when(byte_offset === 0.U){
                            io.data := ((updated_word(15,0)).asSInt.pad(32)).asUInt
                        }.otherwise{
                            io.data := ((updated_word(31,16)).asSInt.pad(32)).asUInt
                        }
                    }
                    is(MemOp.LB){
                        when(byte_offset === 0.U){
                            io.data := updated_word(7,0).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 1.U){
                            io.data := updated_word(15,8).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 2.U){
                            io.data := updated_word(23,16).asSInt.pad(32).asUInt
                        }.otherwise{
                            io.data := updated_word(31,24).asSInt.pad(32).asUInt
                        }
                    }
                }
                io.done := true.B
            }
            state := CacheState.IDLE
        }
    }
}   
  when((io.start || state =/= CacheState.IDLE) && false.B) {
    printf("ICACHE cycle: state=%d | req=[addr=%x wdata=%x r=%d w=%d] | idx=%x tag=%x word_off=%x | meta=[status=%b tag=%x] | data_out=%x | data=%x done=%d miss=%d wb=%d | line_valid=%d line_result=%x | wb_addr=%x line_addr=%x\n",
        state.asUInt,
        current_mem_req.address,
        current_mem_req.write_data,
        current_mem_req.read,
        current_mem_req.write,
        cache_index,
        cache_tag,
        word_offset,
        meta_out.valid,
        meta_out.tag,
        data_out,
        io.data,
        io.done,
        io.miss,
        io.wb,
        io.line_valid,
        io.line_result,
        io.wb_addr,
        io.line_addr
    )
}

    for (w <- 0 until WAYS) {
        when(data_wr_en(w)) { data_array(w).write(write_addr, data_wr_data) }
        when(meta_wr_en(w)) { meta_array(w).write(write_addr, meta_wr_data) }
    }
}