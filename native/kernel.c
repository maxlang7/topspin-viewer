#define _GNU_SOURCE
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#define CARRY8(a,b) ((uint64_t)(a)+(uint64_t)(b)<(uint64_t)(a))
typedef unsigned int uint;
typedef unsigned long ulong;
typedef unsigned char byte;
typedef uint32_t undefined4;
typedef uint64_t undefined8;
/* Win32 proc1d.exe 0x428f76 and 0x4290aa reduce both phase increments
 * modulo 360, then add 360 to negative remainders before accumulation. */
static double processing_angle(double degrees) {
    double reduced = fmod(degrees, 360.0);
    return reduced < 0.0 ? reduced + 360.0 : reduced;
}
/* Numeric routines reconstructed from local TopSpin 5.0.0 ELF analysis.
 * Vendor files are used only by differential tests, never linked here.
 * Preserve arithmetic order. Compile with floating-point contraction disabled.
 */

void reconstructed_ft(double *param_1,double *param_2,double *param_3,uint param_4)

{
  double *pdVar1;
  double *pdVar2;
  double dVar3;
  long lVar4;
  long lVar5;
  uint uVar6;
  double *pdVar7;
  double *pdVar8;
  double *pdVar9;
  long lVar10;
  double *pdVar11;
  uint uVar12;
  long lVar13;
  double *pdVar14;
  ulong uVar15;
  double dVar16;
  double dVar17;
  double dVar18;
  
  if ((param_4 & 3) != 0) {
    uVar15 = 0;
    pdVar9 = param_2;
    do {
      dVar18 = *param_1;
      lVar13 = uVar15 * 8;
      dVar17 = param_1[1];
      pdVar8 = param_3 + (param_4 - uVar15);
      lVar10 = (ulong)(param_4 - (int)uVar15) * -8;
      pdVar7 = param_3;
      pdVar11 = param_3;
      pdVar14 = param_1;
      do {
        lVar4 = lVar13;
        if (pdVar8 <= pdVar11) {
          lVar4 = lVar10;
        }
        pdVar11 = (double *)((long)pdVar11 + lVar4);
        lVar4 = lVar10;
        if (pdVar7 < pdVar8) {
          lVar4 = lVar13;
        }
        dVar3 = *pdVar11;
        lVar5 = lVar13;
        if (pdVar8 <= (double *)((long)pdVar7 + lVar4)) {
          lVar5 = lVar10;
        }
        pdVar7 = (double *)((long)pdVar7 + lVar4 + lVar5);
        if (param_3 == pdVar7) {
          dVar16 = 1.0;
          if (param_3 != pdVar11) {
            dVar16 = -1.0;
          }
        }
        else {
          dVar16 = *pdVar7 / (dVar3 + dVar3);
        }
        pdVar1 = pdVar14 + 2;
        pdVar2 = pdVar14 + 3;
        pdVar14 = pdVar14 + 2;
        dVar18 = dVar18 + *pdVar1 * dVar16 + dVar3 * *pdVar2;
        dVar17 = dVar17 + (dVar16 * *pdVar2 - dVar3 * *pdVar1);
      } while (pdVar14 < param_1 + ((ulong)(param_4 * 2) - 2));
      *pdVar9 = dVar18;
      uVar15 = (ulong)((int)uVar15 + 1);
      pdVar7 = pdVar9 + 2;
      pdVar9[1] = dVar17;
      pdVar9 = pdVar7;
    } while (pdVar7 < param_2 + param_4 * 2);
    return;
  }
  uVar6 = param_4 >> 2;
  uVar12 = 0;
  pdVar9 = param_2;
  do {
    dVar18 = *param_1;
    dVar17 = param_1[1];
    pdVar7 = param_3;
    pdVar11 = param_1;
    do {
      lVar10 = (ulong)uVar12 * 8;
      if (param_3 + ((ulong)param_4 - (ulong)uVar12) <= pdVar7) {
        lVar10 = (ulong)(param_4 - uVar12) * -8;
      }
      pdVar7 = (double *)((long)pdVar7 + lVar10);
      lVar10 = (ulong)(param_4 - uVar6) * -8;
      if (pdVar7 < param_3 + ((ulong)param_4 - (ulong)uVar6)) {
        lVar10 = (ulong)uVar6 * 8;
      }
      pdVar14 = pdVar11 + 2;
      pdVar8 = pdVar11 + 3;
      pdVar11 = pdVar11 + 2;
      dVar18 = dVar18 + *pdVar14 * *(double *)(lVar10 + (long)pdVar7) + *pdVar8 * *pdVar7;
      dVar17 = dVar17 + (*(double *)(lVar10 + (long)pdVar7) * *pdVar8 - *pdVar14 * *pdVar7);
    } while (pdVar11 < param_1 + ((ulong)(param_4 * 2) - 2));
    *pdVar9 = dVar18;
    uVar12 = uVar12 + 1;
    pdVar7 = pdVar9 + 2;
    pdVar9[1] = dVar17;
    pdVar9 = pdVar7;
  } while (pdVar7 < param_2 + param_4 * 2);
  return;
}



void reconstructed_radix4(double *param_1,long param_2,uint param_3,int param_4,byte param_5)

{
  double *pdVar1;
  ulong uVar2;
  double *pdVar3;
  double *pdVar4;
  double *pdVar5;
  double *pdVar6;
  ulong uVar7;
  double *pdVar8;
  double *pdVar9;
  uint uVar10;
  double *pdVar11;
  double *pdVar12;
  uint uVar13;
  double *pdVar14;
  double *pdVar15;
  double *pdVar16;
  double *pdVar17;
  double *pdVar18;
  double *pdVar19;
  ulong uVar20;
  ulong uVar21;
  double dVar22;
  double dVar23;
  double dVar24;
  double dVar25;
  double dVar26;
  double dVar27;
  double dVar28;
  double *local_78;
  uint local_60;
  uint local_5c;
  double *local_58;
  
  uVar2 = (ulong)(param_3 * 8) / (ulong)((-(uint)((param_3 & 0xaaaaaaaa) == 0) & 0xfffffffc) + 8);
  uVar7 = (ulong)(param_3 * 2);
  uVar10 = 0x10 << (param_5 & 0x1f);
  uVar13 = (uint)uVar2 >> (param_5 & 0x1f);
  local_78 = param_1 + uVar7;
  local_60 = uVar13;
  local_5c = uVar10;
  local_58 = param_1;
  while( true ) {
    do {
      uVar20 = (ulong)local_60;
      uVar21 = (ulong)local_5c;
      pdVar1 = (double *)(param_2 + uVar20 * 2);
      pdVar3 = (double *)(uVar20 + uVar2 + param_2);
      pdVar4 = local_58;
      do {
        pdVar6 = (double *)((long)pdVar4 + uVar21);
        pdVar11 = (double *)((long)pdVar6 + uVar21);
        pdVar9 = (double *)((long)pdVar11 + uVar21);
        dVar24 = *pdVar4 + *pdVar11;
        dVar23 = *pdVar4 - *pdVar11;
        dVar22 = *pdVar6 - *pdVar9;
        dVar25 = *pdVar6 + *pdVar9;
        *pdVar4 = dVar24 + dVar25;
        *pdVar11 = dVar24 - dVar25;
        dVar24 = pdVar6[1] + pdVar9[1];
        dVar25 = pdVar6[1] - pdVar9[1];
        *pdVar6 = dVar23 + dVar25;
        *pdVar9 = dVar23 - dVar25;
        dVar23 = pdVar4[1] - pdVar11[1];
        dVar25 = pdVar4[1] + pdVar11[1];
        pdVar4[1] = dVar24 + dVar25;
        pdVar11[1] = dVar25 - dVar24;
        pdVar6[1] = dVar23 - dVar22;
        pdVar9[1] = dVar22 + dVar23;
        pdVar8 = pdVar9;
        pdVar14 = pdVar1;
        pdVar16 = (double *)(param_2 + uVar20);
        pdVar18 = (double *)((long)pdVar1 + uVar20);
        pdVar19 = pdVar3;
        if (pdVar1 != pdVar3) {
          do {
            pdVar9 = pdVar8 + 2;
            pdVar5 = pdVar4 + 2;
            pdVar17 = (double *)((long)pdVar16 + uVar20);
            pdVar12 = pdVar11 + 2;
            dVar23 = pdVar6[2] * *pdVar19 + pdVar6[3] * *pdVar16;
            dVar22 = *pdVar16 * pdVar6[2] - pdVar6[3] * *pdVar19;
            dVar25 = pdVar8[3] * *pdVar18 + *pdVar9 * *(double *)((long)pdVar18 + uVar2);
            dVar24 = *pdVar18 * *pdVar9 - pdVar8[3] * *(double *)((long)pdVar18 + uVar2);
            pdVar15 = (double *)((long)pdVar14 + uVar20 * 2);
            dVar28 = dVar23 + dVar25;
            dVar23 = dVar23 - dVar25;
            dVar26 = dVar22 + dVar24;
            dVar22 = dVar22 - dVar24;
            dVar27 = *pdVar12 * *(double *)((long)pdVar14 + uVar2) + pdVar11[3] * *pdVar14;
            dVar24 = *pdVar14 * *pdVar12 - pdVar11[3] * *(double *)((long)pdVar14 + uVar2);
            dVar25 = *pdVar5 + dVar27;
            dVar27 = *pdVar5 - dVar27;
            *pdVar5 = dVar28 + dVar25;
            *pdVar12 = dVar25 - dVar28;
            pdVar6[2] = dVar27 - dVar22;
            *pdVar9 = dVar22 + dVar27;
            dVar22 = pdVar4[3] - dVar24;
            dVar24 = dVar24 + pdVar4[3];
            pdVar4[3] = dVar22 - dVar26;
            pdVar11[3] = dVar26 + dVar22;
            pdVar6[3] = dVar24 - dVar23;
            pdVar8[3] = dVar24 + dVar23;
            pdVar4 = pdVar5;
            pdVar6 = pdVar6 + 2;
            pdVar8 = pdVar9;
            pdVar11 = pdVar12;
            pdVar14 = pdVar15;
            pdVar16 = pdVar17;
            pdVar18 = (double *)((long)pdVar18 + uVar20 * 3);
            pdVar19 = (double *)((long)pdVar19 + uVar20);
          } while (pdVar15 != (double *)((long)pdVar17 + uVar2));
        }
        pdVar4 = pdVar9 + 2;
      } while (pdVar4 < local_78);
      local_60 = local_60 >> 2;
      local_5c = local_5c << 2;
    } while (7 < local_60);
    if (param_1 + (uint)(param_4 * 2) <= local_78) break;
    local_58 = local_58 + uVar7 * 2;
    local_78 = local_78 + uVar7 * 2;
    local_60 = uVar13;
    local_5c = uVar10;
    if (param_1 + (uint)(param_4 * 2) <= local_58) {
      return;
    }
  }
  return;
}



void reconstructed_lastradix2(double *param_1,double *param_2,ulong param_3)

{
  ulong uVar1;
  double *pdVar2;
  double dVar3;
  double *pdVar4;
  double *pdVar5;
  long lVar6;
  double *pdVar7;
  double *pdVar8;
  double *pdVar9;
  double *pdVar10;
  double *pdVar11;
  double dVar12;
  double dVar13;
  double dVar14;
  
  dVar3 = *param_1;
  pdVar7 = param_1 + (param_3 & 0xffffffff);
  pdVar2 = param_2 + ((param_3 >> 1 & 0x7fffffff) - 2);
  dVar12 = *pdVar7;
  dVar13 = pdVar7[1];
  pdVar8 = param_1 + 2;
  pdVar4 = pdVar7 + 2;
  *pdVar7 = dVar3 - dVar12;
  *param_1 = dVar12 + dVar3;
  dVar3 = param_1[1];
  pdVar7[1] = dVar3 - dVar13;
  param_1[1] = dVar13 + dVar3;
  pdVar7 = pdVar8;
  pdVar5 = pdVar4;
  pdVar9 = pdVar2;
  pdVar11 = param_2 + 2;
  do {
    pdVar10 = pdVar9 + -2;
    dVar3 = *pdVar11;
    pdVar11 = pdVar11 + 2;
    dVar13 = pdVar5[1] * dVar3 + *pdVar5 * *pdVar9;
    dVar12 = pdVar5[1] * *pdVar9 - dVar3 * *pdVar5;
    dVar3 = *pdVar7;
    *pdVar5 = dVar3 - dVar13;
    *pdVar7 = dVar13 + dVar3;
    dVar3 = pdVar7[1];
    pdVar5[1] = dVar3 - dVar12;
    pdVar7[1] = dVar12 + dVar3;
    pdVar7 = pdVar7 + 2;
    pdVar5 = pdVar5 + 2;
    pdVar9 = pdVar10;
  } while (param_2 != pdVar10);
  lVar6 = (long)pdVar2 - (long)param_2;
  uVar1 = lVar6 - 0x10;
  pdVar7 = (double *)((long)(param_2 + 2) + uVar1);
  pdVar5 = (double *)((long)pdVar8 + lVar6);
  pdVar11 = (double *)(lVar6 + (long)pdVar4);
  dVar3 = *pdVar5;
  pdVar9 = (double *)((long)pdVar2 - (uVar1 & 0xfffffffffffffff0));
  dVar12 = *pdVar11;
  *pdVar11 = dVar3 - pdVar11[1];
  *pdVar5 = pdVar11[1] + dVar3;
  dVar3 = pdVar5[1];
  pdVar11[1] = dVar12 + dVar3;
  pdVar5[1] = dVar3 - dVar12;
  pdVar5 = (double *)((long)pdVar4 + lVar6 + 0x10);
  pdVar8 = (double *)((long)pdVar8 + lVar6 + 0x10);
  do {
    dVar3 = *pdVar7;
    pdVar7 = pdVar7 + -2;
    dVar12 = *pdVar9;
    pdVar9 = pdVar9 + 2;
    dVar14 = pdVar5[1] * dVar3 - *pdVar5 * dVar12;
    dVar13 = *pdVar5 * dVar3 + pdVar5[1] * dVar12;
    dVar3 = *pdVar8;
    *pdVar5 = dVar3 - dVar14;
    dVar12 = pdVar8[1];
    *pdVar8 = dVar14 + dVar3;
    pdVar5[1] = dVar13 + dVar12;
    pdVar8[1] = dVar12 - dVar13;
    pdVar5 = pdVar5 + 2;
    pdVar8 = pdVar8 + 2;
  } while (pdVar7 != (double *)((~uVar1 & 0xfffffffffffffff0) + (long)pdVar2));
  return;
}



void reconstructed_setsin4(double *param_1,uint param_2)

{
  double *pdVar1;
  double dVar2;
  double __x;
  
  if (param_1 < param_1 + param_2) {
    dVar2 = 0.0;
    __x = 0.0;
    pdVar1 = param_1;
    while( true ) {
      __x = __x + 6.283185307179586 / (double)param_2;
      *pdVar1 = dVar2;
      if (param_1 + param_2 <= pdVar1 + 1) break;
      dVar2 = sin(__x);
      pdVar1 = pdVar1 + 1;
    }
  }
  return;
}



void reconstructed_setsin1(double *param_1,uint param_2)

{
  double *pdVar1;
  double dVar2;
  double __x;
  
  if (param_1 < param_1 + param_2) {
    dVar2 = 0.0;
    __x = 0.0;
    pdVar1 = param_1;
    while( true ) {
      __x = __x + 1.5707963267948966 / (double)param_2;
      *pdVar1 = dVar2;
      if (param_1 + param_2 <= pdVar1 + 1) break;
      dVar2 = sin(__x);
      pdVar1 = pdVar1 + 1;
    }
  }
  return;
}



void reconstructed_qnegconj(long param_1,uint param_2,int param_3,int param_4)

{
  ulong *puVar1;
  ulong *puVar2;
  ulong *puVar3;
  
  puVar3 = (ulong *)(param_1 + 8);
  puVar1 = (ulong *)(param_1 + (ulong)param_2 * 8);
  if (param_3 == 0) {
    if ((param_4 != 0) && (puVar3 < puVar1)) {
      do {
        puVar2 = puVar3 + 2;
        *puVar3 = *puVar3 ^ 0x8000000000000000;
        puVar3 = puVar2;
      } while (puVar2 < puVar1);
      return;
    }
  }
  else {
    if (param_4 == 0) {
      puVar3 = (ulong *)(param_1 + 0x10);
    }
    if (puVar3 < puVar1) {
      do {
        puVar2 = puVar3 + 4;
        *puVar3 = *puVar3 ^ 0x8000000000000000;
        puVar3[1] = puVar3[1] ^ 0x8000000000000000;
        puVar3 = puVar2;
      } while (puVar2 < puVar1);
      return;
    }
  }
  return;
}



int reconstructed_getscale(double *param_1,int param_2)

{
  double *pdVar1;
  int iVar2;
  double dVar3;
  double dVar4;
  
  pdVar1 = param_1 + param_2;
  if (param_1 < pdVar1) {
    dVar3 = 0.0;
    do {
      dVar4 = *param_1;
      param_1 = param_1 + 1;
      if (dVar4 < 0.0) {
        dVar4 = -dVar4;
      }
      if (dVar4 <= dVar3) {
        dVar4 = dVar3;
      }
      dVar3 = dVar4;
    } while (param_1 < pdVar1);
    dVar3 = dVar3 * 3.725290298461914e-09;
    if ((dVar3 != 0.0) && (dVar3 <= 0x1.fffffffffffffp+1023)) {
      iVar2 = 0;
      for (; 2.0 <= dVar3; dVar3 = dVar3 * 0.5) {
        iVar2 = iVar2 + 1;
      }
      if (1.0 <= dVar3) {
        return iVar2;
      }
      do {
        dVar3 = dVar3 + dVar3;
        iVar2 = iVar2 + -1;
      } while (dVar3 < 1.0);
      return iVar2;
    }
  }
  return -0x20;
}



void reconstructed_phase(double param_1,double param_2,double *param_3,int param_4,double *param_5,ulong param_6)

{
  double *pdVar1;
  double dVar2;
  double dVar3;
  double dVar4;
  double dVar5;
  double dVar6;
  double *pdVar7;
  double dVar8;
  double local_70;
  double local_68;
  double local_38;
  double local_30 [2];
  
  pdVar1 = param_3 + param_4 * 2;
  param_2 = param_2 * 0.017453292519943295;
  param_1 = param_1 * 0.017453292519943295;
  if ((param_6 & 0x200000) == 0) {
    if (param_2 == 0.0) {
      sincos(param_1,local_30,&local_38);
      if (param_3 < pdVar1) {
        do {
          dVar2 = *param_3;
          pdVar7 = param_3 + 2;
          *param_3 = local_38 * dVar2 - local_30[0] * param_3[1];
          param_3[1] = param_3[1] * local_38 + dVar2 * local_30[0];
          param_3 = pdVar7;
        } while (pdVar7 < pdVar1);
        return;
      }
    }
    else if (param_3 < pdVar1) {
      do {
        pdVar7 = param_3 + 2;
        sincos(param_1,local_30,&local_38);
        dVar2 = *param_3;
        param_1 = param_1 + param_2;
        *param_3 = local_38 * dVar2 - local_30[0] * param_3[1];
        param_3[1] = local_38 * param_3[1] + local_30[0] * dVar2;
        param_3 = pdVar7;
      } while (pdVar7 < pdVar1);
      return;
    }
  }
  else {
    dVar2 = param_5[2];
    local_70 = *param_5;
    local_68 = param_5[1];
    dVar3 = param_5[3];
    dVar4 = param_5[4];
    dVar5 = param_5[5];
    if ((param_6 & 0x10000) != 0) {
      param_2 = param_2 / (2.0 / (double)param_4);
      local_68 = param_2 + local_68;
      local_70 = param_1 + param_2 + local_70;
    }
    if (param_3 < pdVar1) {
      dVar8 = -1.0;
      do {
        pdVar7 = param_3 + 2;
        sincos(((((dVar5 * dVar8 + dVar4) * dVar8 + dVar3) * dVar8 + dVar2) * dVar8 + local_68) *
               dVar8 + local_70,local_30,&local_38);
        dVar6 = *param_3;
        dVar8 = dVar8 + 2.0 / (double)param_4;
        *param_3 = local_38 * dVar6 - local_30[0] * param_3[1];
        param_3[1] = local_38 * param_3[1] + local_30[0] * dVar6;
        param_3 = pdVar7;
      } while (pdVar7 < pdVar1);
    }
  }
  return;
}



double reconstructed_post(double *param_1,double *param_2,int param_3)

{
  double *pdVar1;
  long lVar2;
  double *pdVar3;
  double *pdVar4;
  double *pdVar5;
  double *pdVar6;
  double *pdVar7;
  double *pdVar8;
  double dVar9;
  double dVar10;
  double dVar11;
  double dVar12;
  double dVar13;
  double dVar14;
  double dVar15;
  double *local_70;
  double *local_68;
  double local_48;
  double local_40 [2];
  
  local_70 = param_1 + 2;
  pdVar1 = param_1 + ((ulong)(uint)(param_3 * 2) - 2);
  dVar9 = *param_1;
  dVar10 = param_1[1];
  param_2[1] = 0.0;
  dVar11 = dVar9 - dVar10;
  dVar9 = dVar9 + dVar10;
  *param_2 = dVar9 + dVar9;
  local_68 = param_2;
  if (local_70 < pdVar1) {
    dVar9 = 0.0;
    pdVar3 = param_2 + (uint)(param_3 * 2);
    pdVar4 = pdVar1;
    pdVar6 = local_70;
    pdVar8 = param_2;
    do {
      dVar9 = dVar9 + 3.141592653589793 / (double)param_3;
      pdVar7 = pdVar6 + 2;
      pdVar5 = pdVar4 + -2;
      sincos(dVar9,local_40,&local_48);
      dVar10 = *pdVar4 - *pdVar6;
      dVar14 = *pdVar4 + *pdVar6;
      dVar15 = pdVar6[1] + pdVar4[1];
      dVar13 = pdVar6[1] - pdVar4[1];
      dVar12 = local_48 * dVar10 - local_40[0] * dVar15;
      dVar10 = local_48 * dVar15 + local_40[0] * dVar10;
      pdVar8[3] = dVar13 + dVar12;
      pdVar3[-1] = dVar12 - dVar13;
      pdVar8[2] = dVar14 + dVar10;
      pdVar3[-2] = dVar14 - dVar10;
      pdVar3 = pdVar3 + -2;
      pdVar4 = pdVar5;
      pdVar6 = pdVar7;
      pdVar8 = pdVar8 + 2;
    } while (pdVar7 < pdVar5);
    lVar2 = ((ulong)((long)pdVar1 + (-0x11 - (long)param_1)) >> 5) + 1;
    local_70 = local_70 + lVar2 * 2;
    local_68 = param_2 + lVar2 * 2;
  }
  local_68[2] = *local_70 + *local_70;
  local_68[3] = local_70[1] * -2.0;
  return dVar11 + dVar11;
}



void reconstructed_bitdigrev(long param_1,int param_2)

{
  undefined8 *puVar1;
  undefined8 *puVar2;
  undefined8 uVar3;
  uint uVar4;
  uint uVar5;
  uint uVar6;
  uint uVar7;
  uint uVar8;
  
  uVar6 = param_2 * 2;
  if ((uVar6 & 0x55555554) == 0) {
    uVar7 = 0xaaaaaaaa;
    uVar5 = 0x55555554;
    uVar6 = uVar6 - 2;
    uVar8 = 0;
    if (uVar6 == 0) {
      return;
    }
  }
  else {
    uVar6 = uVar6 - 2;
    uVar8 = 2;
    uVar7 = 0x55555554;
    uVar5 = 0xaaaaaaa8;
  }
  do {
    uVar4 = uVar6 * 2 & uVar5 | uVar6 >> 1 & uVar7;
    if (uVar6 < uVar4) {
      puVar1 = (undefined8 *)(param_1 + ((ulong)uVar4 + (ulong)(uVar6 & uVar8)) * 8);
      puVar2 = (undefined8 *)(param_1 + (ulong)uVar6 * 8);
      uVar3 = *puVar2;
      *puVar2 = *puVar1;
      *puVar1 = uVar3;
      uVar3 = puVar2[1];
      puVar2[1] = puVar1[1];
      puVar1[1] = uVar3;
    }
    uVar6 = uVar6 - 2;
  } while (uVar6 != 0);
  return;
}



void reconstructed_bitrev(long param_1,uint param_2)

{
  undefined8 *puVar1;
  undefined8 *puVar2;
  undefined8 uVar3;
  uint uVar4;
  uint uVar5;
  uint uVar6;
  
  uVar5 = 2;
  uVar6 = param_2;
  do {
    if (uVar5 < uVar6) {
      puVar1 = (undefined8 *)(param_1 + (ulong)uVar5 * 8);
      puVar2 = (undefined8 *)(param_1 + (ulong)uVar6 * 8);
      uVar3 = *puVar1;
      *puVar1 = *puVar2;
      *puVar2 = uVar3;
      uVar3 = puVar1[1];
      puVar1[1] = puVar2[1];
      puVar2[1] = uVar3;
    }
    uVar4 = param_2;
    if (param_2 <= uVar6) {
      do {
        uVar6 = uVar6 - uVar4;
        uVar4 = uVar4 >> 1;
      } while (uVar4 <= uVar6);
    }
    uVar6 = uVar6 + uVar4;
    uVar5 = uVar5 + 2;
  } while (uVar6 != 1);
  return;
}



void reconstructed_expandzf(long param_1,uint param_2,uint param_3)

{
  int iVar1;
  int iVar2;
  uint uVar3;
  undefined8 *puVar4;
  undefined8 *puVar5;
  int iVar6;
  ulong uVar7;
  undefined8 *puVar8;
  undefined8 *puVar9;
  
  puVar9 = (undefined8 *)(param_1 + (ulong)(param_2 * 2) * 8);
  puVar5 = (undefined8 *)(param_1 + (ulong)(param_3 * 2) * 8);
  iVar2 = (int)((ulong)param_2 / (ulong)param_3);
  iVar1 = iVar2 * 2;
  iVar6 = iVar1;
  if (((ulong)param_2 / (ulong)param_3 & 0xaaaaaaaa) == 0) {
    iVar6 = 0;
    iVar2 = iVar1;
  }
  uVar3 = iVar2 - 2;
  do {
    puVar8 = puVar9 + -(ulong)(uVar3 + iVar6);
    puVar4 = puVar9;
    do {
      puVar4 = puVar4 + -1;
      *puVar4 = 0;
    } while (puVar8 < puVar4);
    uVar7 = ~(((long)puVar9 + -1) - (long)puVar8) & 0xfffffffffffffff8;
    if ((undefined8 *)((long)puVar9 + -1) < puVar8) {
      uVar7 = 0xfffffffffffffff8;
    }
    *(undefined8 *)((long)puVar9 + (uVar7 - 8)) = puVar5[-1];
    puVar8 = (undefined8 *)((long)puVar9 + (uVar7 - 0x10));
    *(undefined8 *)((long)puVar9 + (uVar7 - 0x10)) = puVar5[-2];
    puVar4 = puVar8;
    if (CARRY8((ulong)puVar8,(ulong)uVar3 * -8)) {
      do {
        puVar4 = puVar4 + -1;
        *puVar4 = 0;
      } while (puVar8 + -(ulong)uVar3 < puVar4);
      puVar8 = (undefined8 *)
               ((long)puVar8 +
               (~((long)puVar9 + ((uVar7 - 0x11) - (long)(puVar8 + -(ulong)uVar3))) &
               0xfffffffffffffff8));
    }
    puVar4 = puVar5 + -3;
    puVar5 = puVar5 + -4;
    puVar9 = puVar8 + -2;
    puVar8[-1] = *puVar4;
    puVar8[-2] = *puVar5;
  } while (puVar5 < puVar9);
  return;
}



void reconstructed_convdio(ulong param_1,int param_2,int param_3)

{
  ulong uVar1;
  ulong uVar2;
  bool bVar3;
  double dVar4;
  
  dVar4 = 1.0;
  uVar1 = param_1 + (long)param_2 * 8;
  if (param_3 != 0) {
    if (param_3 < 1) {
      do {
        dVar4 = dVar4 + dVar4;
        if (param_3 == -1) break;
        dVar4 = dVar4 + dVar4;
        param_3 = param_3 + 2;
      } while (param_3 != 0);
    }
    else {
      do {
        dVar4 = dVar4 * 0.5;
        param_3 = param_3 + -1;
      } while (param_3 != 0);
    }
  }
  if (uVar1 <= param_1) {
    return;
  }
  uVar2 = 0;
  do {
    *(int *)(param_1 + uVar2 * 4) = (int)(*(double *)(param_1 + uVar2 * 8) * dVar4);
    bVar3 = uVar2 != uVar1 + ~param_1 >> 3;
    uVar2 = uVar2 + 1;
  } while (bVar3);
  return;
}



void reconstructed_big_radix4(ulong param_1,uint param_2,byte param_3)

{
  double *pdVar1;
  ulong uVar2;
  long lVar3;
  ulong uVar4;
  double *pdVar5;
  double *pdVar6;
  double *pdVar7;
  long lVar8;
  double dVar9;
  double dVar10;
  double dVar11;
  double dVar12;
  double dVar13;
  double __x;
  double dVar14;
  double dVar15;
  double dVar16;
  uint local_58;
  double local_48;
  double local_40 [2];
  
  local_58 = 0x10 << (param_3 & 0x1f);
  do {
    uVar2 = (ulong)local_58;
    lVar8 = -uVar2;
    uVar4 = param_1;
    do {
      pdVar1 = (double *)(uVar4 + uVar2 * 3);
      pdVar5 = (double *)(uVar4 + uVar2);
      pdVar6 = pdVar1;
      __x = -(25.132741228718345 / (double)uVar2);
      do {
        __x = __x + 25.132741228718345 / (double)uVar2;
        pdVar7 = pdVar6 + 2;
        sincos(__x,local_40,&local_48);
        dVar15 = pdVar5[1] * local_40[0] + *pdVar5 * local_48;
        dVar14 = *pdVar5 * local_40[0] - pdVar5[1] * local_48;
        dVar12 = (local_40[0] + local_40[0]) * local_48;
        dVar16 = local_48 * local_48 - local_40[0] * local_40[0];
        dVar13 = local_40[0] * dVar16 + local_48 * dVar12;
        dVar9 = local_48 * dVar16 - local_40[0] * dVar12;
        dVar10 = *(double *)((long)pdVar6 + lVar8 + 8);
        dVar11 = pdVar6[1] * dVar13 + *pdVar6 * dVar9;
        dVar9 = *pdVar6 * dVar13 - dVar9 * pdVar6[1];
        dVar13 = dVar15 + dVar11;
        dVar15 = dVar15 - dVar11;
        dVar11 = dVar14 - dVar9;
        dVar14 = dVar14 + dVar9;
        dVar9 = *(double *)((long)pdVar6 + lVar8) * dVar16 + dVar10 * dVar12;
        dVar12 = *(double *)((long)pdVar6 + lVar8) * dVar12 - dVar10 * dVar16;
        dVar10 = *(double *)((long)pdVar5 + lVar8) + dVar9;
        dVar9 = *(double *)((long)pdVar5 + lVar8) - dVar9;
        *(double *)((long)pdVar5 + lVar8) = dVar13 + dVar10;
        *(double *)((long)pdVar6 + lVar8) = dVar10 - dVar13;
        *pdVar5 = dVar9 - dVar11;
        *pdVar6 = dVar11 + dVar9;
        dVar10 = *(double *)((long)pdVar5 + lVar8 + 8);
        dVar9 = dVar10 + dVar12;
        dVar10 = dVar10 - dVar12;
        *(double *)((long)pdVar5 + lVar8 + 8) = dVar10 - dVar14;
        *(double *)((long)pdVar6 + lVar8 + 8) = dVar10 + dVar14;
        pdVar5[1] = dVar9 - dVar15;
        pdVar6[1] = dVar9 + dVar15;
        pdVar5 = pdVar5 + 2;
        pdVar6 = pdVar7;
      } while (pdVar7 < (double *)((long)pdVar1 + uVar2));
      lVar3 = ((uVar2 - 1 >> 4) + 1) * 0x10;
      if ((double *)((long)pdVar1 + uVar2) < (double *)((long)pdVar1 + 1U)) {
        lVar3 = 0x10;
      }
      uVar4 = lVar3 + (long)pdVar1;
    } while (uVar4 < (param_2 << 4) + param_1);
    local_58 = local_58 << 2;
  } while (local_58 <= (param_2 & 0xfffffff) << 2);
  return;
}

