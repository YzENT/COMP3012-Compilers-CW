# COMP3012-Compilers-CW

A stripped-down and slightly customized compiler for **Fortran**, built for University of Nottingham's COMP3012 coursework.

## Supported Language Features
 
| Feature | Status
|---|---
| Integer variables and assignment | Yes
| Logical variables and assignment | Yes
| Character variables and assignment | Yes
| Read/write integers and logicals | Yes
| Read/write characters | Partial
| Array variables, assignment, and indexing | Partial
| Arithmetic operators | Yes
| Relational operators | Yes
| Logical operators | Yes
| If statement | Yes
| If-then-else statement | Yes
| Do statement | Yes
| Do-while statement | Yes
| Subroutines | Yes
| Functions | Yes

## Not Implemented
 
- Defined types and defined-type variables
- Pointer variables and allocation/deallocation
- Concatenation operator
- Optimisations


## Known Limitations
 
- **Character input:** values can be written but not read back into a variable.
- **Array bounds:** indexing is not validated, so out-of-bounds access is undefined behaviour rather than a caught error.
