/* ---------------------------------------------------------------------- */
/* stdafx.h :                                                             */
/* Include file for standard system include files, or project specific    */
/* include files that are used frequently, but are changed infrequently.  */
/* ---------------------------------------------------------------------- */

#ifndef STDAFX_INCLUDED_
#define STDAFX_INCLUDED_


#if defined (_WIN64) || defined (_WIN32)



/* Expand the g++ shared library "hidden" attribute to nothing */
#define __GCC_DONT_EXPORT




/* Exclude rarely-used stuff from Windows headers */
#define WIN32_LEAN_AND_MEAN

/* Windows Header File(s) */
#include <windows.h>




#else /* Unix or Linux */


#if defined (__linux) && defined (__GNUG__)


#define __GCC_DONT_EXPORT __attribute ((visibility ("hidden")))



#else /* Unix or non-g++ Linux */


/* Expand the g++ shared library "hidden" attribute to nothing */
#define __GCC_DONT_EXPORT




//#error "This is Unix (or Linux with non-g++)!"
//#error "You should add your standard includes in stdafx.h or remove this #error macro line in stdafx.h"

#endif /* (__linux) && (__GNUG__) */


#endif /* WIN32/64 versus Unix/Linux */


#endif /* STDAFX_INCLUDED_ */
