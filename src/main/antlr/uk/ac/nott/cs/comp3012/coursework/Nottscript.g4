grammar Nottscript;

arrayDimension: Int_base10;
arrayStar: '*';
parameterList: Name (COMMA Name)*;
argumentList: expression (COMMA expression)*;
returnArg: RESULT LPAREN Name RPAREN;

programUnit: (subroutineDef | functionDef | derivedDef)* programDef (subroutineDef | functionDef | derivedDef)* ;

programDef: PROGRAM Name declaration* statement* END PROGRAM Name;
subroutineDef: SUBROUTINE Name LPAREN parameterList? RPAREN declaration* statement* END SUBROUTINE Name;
functionDef: FUNCTION Name LPAREN parameterList? RPAREN returnArg? declaration* statement* END FUNCTION Name;
derivedDef: TYPE Name declaration* END TYPE Name;

// Declarations
declaration
    : varTypeName DECLARE Name (COMMA Name)* // integer :: a, b
    | varTypeName LPAREN arrayDimension (COMMA arrayDimension)* RPAREN DECLARE Name (COMMA Name)* // real(5,5) :: more_nums, more_nums2
    | varTypeName POINTER DECLARE Name (COMMA Name)* // real pointer :: x, y
    | varTypeName LPAREN arrayStar (COMMA arrayStar)* RPAREN POINTER DECLARE Name (COMMA Name)* // integer(*,*) pointer :: matrix, matrix2
    ;

varTypeName
    : INTEGER
    | REAL
    | CHARACTER
    | LOGICAL
    | POINTER
    | TYPE LPAREN Name RPAREN
    ;

// Statements
statement
    : assignment
    | subroutineCallStatement
    | ifStatement
    | ifThenElseStatement
    | doStatement
    | doWhileStatement
    | readStatement
    | writeStatement
    | allocStatement
    | deallocStatement
    ;

assignment
    : Name arrayIndex? ASSIGN expression
    | Name FIELD_ACCESS Name arrayIndex? ASSIGN expression // a % m(2,2) = 8
    ;

subroutineCallStatement: CALL Name (LPAREN argumentList? RPAREN); // Only for subroutines
ifStatement: IF (LPAREN expression RPAREN) statement; // One statement only
ifThenElseStatement: IF (LPAREN expression RPAREN) THEN statement* (ELSE statement*)? END IF;
doStatement: DO (Name ASSIGN expression COMMA expression) (COMMA expression)? statement* END DO;
doWhileStatement: DO WHILE (LPAREN expression RPAREN) statement* END DO;
readStatement: READ expression (COMMA expression)*;
writeStatement: WRITE expression (COMMA expression)*;
allocStatement: ALLOCATE Name (COMMA (Name | integers) )?; // allocate y, 10 (second argument could be a variable name instead)
deallocStatement: DEALLOCATE Name; // deallocate y

arrayIndex: LPAREN expression (COMMA expression)* RPAREN;


// Expressions
//expression
//    : expression (PLUS | MINUS | MULT | DIV | POWER) expression
//    | expression (EQ | NEQ | LT | GT | LE | GE) expression
//    | expression (AND | OR) expression
//    | expression CONCAT expression
//    | LPAREN expression RPAREN
//    /*
////    | Name LPAREN argumentList? RPAREN // Function calls, expression since it MUST return a value
////    | Name arrayIndex? // Array
//    */
//    | Name LPAREN argumentList? RPAREN // Function calls and array (ambiguous)
//    | Name FIELD_ACCESS Name arrayIndex? // field access --- a % m(2,2)
//    | Name
//    | constants
//    ;

expression
    : <assoc=left> expression (OR) expression                           // Precedence 1 (lowest)
    | <assoc=left> expression (AND) expression                          // Precedence 2
    | <assoc=left> expression (EQ | NEQ | LT | GT | LE | GE) expression // Precedence 3
    | <assoc=left> expression CONCAT expression                         // Precedence 4
    | <assoc=left> expression (PLUS | MINUS) expression                 // Precedence 5
    | <assoc=left> expression (MULT | DIV) expression                   // Precedence 6
    | <assoc=right> expression POWER expression                         // Precedence 7 (RIGHT!)
    | LPAREN expression RPAREN                                          // Precedence 8
    | Name LPAREN argumentList? RPAREN                                  // Precedence 9
    | Name FIELD_ACCESS Name arrayIndex?                                // Precedence 9
    | Name                                                              // Precedence 9
    | constants                                                         // Precedence 9 (highest)
    ;

// Constants
constants
    : integers
    | Reals
    | Boolean
    | String
    ;

integers
    : Int_base10
    | Int_base2
    | Int_base7
    | Int_base16
    ;

/* Keywords */
ALLOCATE: 'allocate';
BREAK: 'break';
CALL: 'call';
CHARACTER: 'character';
DEALLOCATE: 'deallocate';
DO: 'do';
ELSE: 'else';
END: 'end';
FUNCTION: 'function';
IF: 'if';
INTEGER: 'integer';
LOGICAL: 'logical';
POINTER: 'pointer';
PROGRAM: 'program';
READ: 'read';
REAL: 'real';
RESULT: 'result';
SUBROUTINE: 'subroutine';
THEN: 'then';
TYPE: 'type';
WHILE: 'while';
WRITE: 'write';

WhiteSpace: [ \t\r\n] -> skip;
Comment: '!' ~[\r\n]* -> skip; /* start with ! and run to end of line */

/* Arithmetic (only integers operations) */
MULT: '*';
POWER: '**';
DIV: '/';
PLUS: '+';
MINUS: '-';

/* Assignable values */
Int_base10: [+-]?[0-9]+;
Int_base2: 'b"' [0-1]+ '"';
Int_base7: 'o"' [0-7]+ '"';
Int_base16: 'z"' [0-9a-f]+ '"';
Reals: [+-]?[0-9]* '.' [0-9]*;
Boolean: '.' ('true' | 'false') '.';
String : '"' ('""' | ~["\r\n])* '"' ;

/* Logical Operators */
AND: '.and.';
OR: '.or.';

/* Relational Operators */
LT: ('<' | '.lt.'); // Less than
LE: ('<=' | '.le.'); // Less than or equal to
GT: ('>' | '.gt.'); // Greater than
GE: ('>=' | '.ge.'); // Greater than or equal to
EQ: ('==' | '.eq.'); // Equal to
NEQ: ('/=' | '.neq.'); // Not equal to

/* Special Characters and Delimiters */
ASSIGN: '=';
FIELD_ACCESS: '%'; // NOT MODULO (spec page 7-9)
CONCAT: '//'; // String ONLY
LPAREN: '(';
RPAREN: ')';
LBRACK: '[';
RBRACK: ']';
COMMA: ',';
COLON: ':';
DECLARE: '::';

/* Identifiers - comes after special tokens */
Name: [a-z][a-z0-9_]*; /* start with a letter, followed by any alphanumeric number/underscores*/