package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage

class Registers(physical_registers_log: Int) extends Module {
    val io = IO(new Bundle {
        val write_enable = Input(Bool())
        val write_address = Input(UInt(physical_registers_log.W))
        val in = Input(UInt(32.W))

        val read_address_a = Input(UInt(physical_registers_log.W))
        val read_address_b = Input(UInt(physical_registers_log.W))

        val out_a = Output(UInt(32.W))
        val out_b = Output(UInt(32.W))
    })

    val regs = RegInit(VecInit(Seq.fill((1 << physical_registers_log).toInt)(0.U(32.W))))

    io.out_a := regs(io.read_address_a)
    io.out_b := regs(io.read_address_b)

    when(io.write_enable && (io.write_address =/= 0.U)) {
        regs(io.write_address) := io.in
    }
}
