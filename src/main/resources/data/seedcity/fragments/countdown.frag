; count a register down to zero, showing it on an actuator
SET $R1 $N1
$L1:
OUT $A1 $R1
WAIT 1
SUB $R1 1
JZ $R1 $L2
JMP $L1
$L2:
OUT $A1 0
