ENTRY(_start)
SECTIONS {
    . = 0x00000000;
    .text : {
        KEEP(*(.init))
        *(.text.unlikely*)
        *(.text.startup*)
        *(.text.hot*)
        *(.text*)
    }
    .rodata : {
        *(.rodata*)
        *(.srodata*)
    }
    .data : {
        *(.data*)
        *(.sdata*)
    }
    .bss : {
        . = ALIGN(4);
        __bss_start = .;
        *(.bss*)
        *(.sbss*)
        *(COMMON)
        . = ALIGN(4);
        __bss_end = .;
    }
    _end = .;
}
