package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage


class MALU_Sub(val width: Int = 32) extends Module {
    val io = IO(new Bundle {
        val func7 = Input(UInt(7.W));
        val func3 = Input(UInt(3.W));
        val a = Input(UInt(width.W)); // First operand
        val b = Input(UInt(width.W)); // Second operand
        val output = Output(UInt(width.W)); // Result of the operation
        val ready = Output(Bool())
        val valid = Output(Bool())
        val start = Input(Bool())
     
    })
    io.output := 0.U;
    val m_alu = Wire(UInt(width.W));
    m_alu := 0.U

    val div = Module(new Div())
    io.ready := div.io.ready

    

  
    div.io.a := io.a
    div.io.b := io.b
    div.io.func3 := io.func3
    div.io.start := false.B



    val a_s = io.a.asSInt
    val b_s = io.b.asSInt
    val a_ext = Cat(io.a(31), io.a).asSInt
    val b_ext = Cat(0.U(1.W), io.b).asSInt


    val p_mul     = RegEnable(((a_s * b_s).asUInt)(31, 0),    0.U(width.W), io.start)
    val p_mulh    = RegEnable((a_s * b_s).asUInt(63, 32),     0.U(width.W), io.start)
    val p_mulhsu  = RegEnable((a_ext * b_ext).asUInt(63, 32), 0.U(width.W), io.start)
    val p_mulhu   = RegEnable((io.a * io.b)(63, 32),          0.U(width.W), io.start)
    val mul_valid = RegNext(io.start && !io.func3(2), false.B)

    when(io.start){
          switch(io.func3) {
        is("b100".U,"b101".U,"b110".U, "b111".U){
            div.io.start:=true.B
            // m_alu := div.io.output

        }

    }

    }

    switch(io.func3) {
        //MUL
        is("b000".U) {
                m_alu := p_mul
        }
        //MULH
        is("b001".U) {
            m_alu := p_mulh
        }
        //MULHSU
        is("b010".U) {
            m_alu := p_mulhsu
        }
        //MULHU
        is("b011".U) {
            m_alu := p_mulhu
        }

    }

    io.valid  := Mux(io.func3(2), div.io.valid, mul_valid)
    io.output := Mux(io.func3(2),div.io.output,m_alu)

  }