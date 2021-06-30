/* -------------------------------------------------------- */
/* dllmain.cpp : Defines the entry point for a Windows DLL  */
/* -------------------------------------------------------- */

#ifndef STDAFX_INCLUDED_
#include "stdafx.h"
#endif /* STDAFX_INCLUDED_ */

int __stdcall DllMain(HMODULE /*hModule*/, unsigned long ul_reason_for_call, void* /*lpReserved*/)
{
    switch (ul_reason_for_call)
    {
        case DLL_PROCESS_ATTACH:
        case DLL_THREAD_ATTACH:
        case DLL_THREAD_DETACH:
        case DLL_PROCESS_DETACH:
            break;
    }
    return TRUE;
}

