package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import scala.math._

class RenamedInstruction(physical_registers_log: Int, reorder_size_log: Int) extends Bundle {
    val rs1 = UInt(physical_registers_log.W)
    val rs2 = UInt(physical_registers_log.W)
    val rd = UInt(physical_registers_log.W)
    val immediate = UInt(32.W)
    val opcode = UInt(7.W)
    val func3 = UInt(3.W)
    val func7 = UInt(7.W)
    val write_mode = WriteMode()
    val instruction_pointer = UInt(32.W)
    val predicted_instruction_pointer = UInt(32.W)
    val pe_type = PeType()
    val reorder_pointer = UInt(reorder_size_log.W)
}

/**
  * Here's a specification for the behaviour of the rename stage.
  * 
  * The rename instruction stage consists of two canonical structures, the rename table
  * and free register queue.
  * 
  * The rename table maps architectural registers to physical registers.
  * The free register queue tracks which physical registers are free to map.
  * It is also the last in order stage and thus is after which the instruction enters
  * the reorder buffer.
  * 
  * The rename stage only takes one cycle.
  * 
  * The rename stage is only ready when the next module is ready, the reorder buffer is not full, and there is no incoming
  * instruction while the free register queue is empty.
  * 
  * In order to rename an instruction the following must occur:
  *  - rs1 and rs2 are mapped to physical registers
  *  - a physical register is allocated by popping from the free register queue
  *  - rd is mapped to the popped physical register
  *  - the rename table is updated to map rd to the popped register
  *  - the old register is stored with the instruction for freeing later
  *  - update the renamed instruction with its pointer into the reorder buffer
  * 
  * When an instruction commits, the 'old' register stored with the instruction is
  * pushed onto the free register queue to be reused.
  */

class RenameStage(physical_registers_log: Int, reorder_size_log: Int) extends Module {
    val io = IO(new Bundle {
        val next_ready = Input(Bool())

        val decoded_instruction = Input(Valid(new DecodedInstruction()))

        val reorder_buffer_head = Input(UInt(8.W))
        val reorder_buffer_full = Input(Bool())

        val renamed_instruction = Output(Valid(new RenamedInstruction(physical_registers_log, reorder_size_log)))

        val free_register = Input(Valid(UInt(physical_registers_log.W)))

        val flush = Input(Bool())

        val ready = Output(Bool())
    })

    val physical_registers = 1 << physical_registers_log

    val free_register_queue = RegInit(VecInit(Seq.tabulate(physical_registers)(i => i.U(physical_registers_log.W))))
    val head = RegInit(0.U(8.W))
    val tail = RegInit(0.U(8.W))
    val full = RegInit(false.B)

    val empty = tail === head && !full

    def push(register: UInt) = {
        head := (head + 1.U) % physical_registers.U

        when((head + 1.U) % physical_registers.U === tail) {
            full := true.B
        }
    }

    def pop(): UInt = {
        tail := (tail + 1.U) % physical_registers.U

        full := false.B

        return free_register_queue(tail)
    }

    val rename_map = RegInit(VecInit(Seq.fill(32)(0.U(physical_registers_log.W))))

    val renamed_instruction = RegInit(0.U.asTypeOf(new RenamedInstruction(physical_registers_log, reorder_size_log)))
    val renamed_valid = RegInit(false.B)

    io.renamed_instruction.bits := renamed_instruction
    io.renamed_instruction.valid := renamed_valid

    when(io.next_ready) {
        renamed_valid := false.B
    }

    when(io.next_ready && io.decoded_instruction.valid && !empty) {
        renamed_instruction.rs1 := rename_map(io.decoded_instruction.bits.rs1)
        renamed_instruction.rs2 := rename_map(io.decoded_instruction.bits.rs2)
        
        val write_register = pop()

        renamed_instruction.rd := write_register
        
        val old_register = rename_map(io.decoded_instruction.bits.rd)
        
        rename_map(io.decoded_instruction.bits.rd) := write_register

        renamed_instruction.immediate := io.decoded_instruction.bits.immediate
        renamed_instruction.opcode := io.decoded_instruction.bits.opcode
        renamed_instruction.func3 := io.decoded_instruction.bits.func3
        renamed_instruction.func7 := io.decoded_instruction.bits.func7
        renamed_instruction.write_mode := io.decoded_instruction.bits.write_mode
        renamed_instruction.instruction_pointer := io.decoded_instruction.bits.instruction_pointer
        renamed_instruction.predicted_instruction_pointer := io.decoded_instruction.bits.predicted_instruction_pointer
        renamed_instruction.pe_type := io.decoded_instruction.bits.pe_type
        renamed_instruction.reorder_pointer := io.reorder_buffer_head

        renamed_valid := true.B
    }

    when(io.free_register.valid) {
        push(io.free_register.bits)
    }

    // We may proceed even if the free register queue is empty to not stall previous stages as
    // that stage isn't directly feeding us, for example not stalling fetch
    io.ready := io.next_ready && (!empty || !io.decoded_instruction.valid) && !io.reorder_buffer_full
}
