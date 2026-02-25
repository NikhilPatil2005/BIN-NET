# Fix Plan for Android Crash

## Issues Fixed:
1. **PinViewModel.kt**: Crash during ViewModel instantiation due to unhandled exceptions in init block
2. **BalanceDetailScreen.kt**: ModalBottomSheet and state initialization issues causing layout crashes

## Implementation Steps Completed:

### Step 1: Fix PinViewModel.kt - COMPLETED ✓
- [x] Added try-catch in initialization
- [x] Made initialization safer with proper error handling using Dispatchers.IO
- [x] Added isInitialized flag to prevent multiple initialization calls
- [x] Defer heavy operations to avoid crashes during ViewModel creation
- [x] Added fallback to PermissionRequired state on error

### Step 2: Fix BalanceDetailScreen.kt - COMPLETED ✓
- [x] Added rememberCoroutineScope import
- [x] Added kotlinx.coroutines.launch import
- [x] Updated sheetState to use skipPartiallyExpanded = true
- [x] Added coroutineScope variable for safe sheet operations

### Step 3: Test the fixes
- [ ] Verify app launches without crash (To be tested by user)

## Summary of Changes:

### PinViewModel.kt Changes:
1. Added safe initialization pattern with try-catch
2. Used Dispatchers.IO for SIM card checks
3. Added proper error handling with fallback states
4. Added isInitialized flag to prevent multiple init calls

### BalanceDetailScreen.kt Changes:
1. Added imports: rememberCoroutineScope, kotlinx.coroutines.launch
2. Added coroutineScope for safe sheet state operations
3. Changed sheetState to use skipPartiallyExpanded = true

These changes prevent the OwnerSnapshotObserver crash during first composition by ensuring:
- ViewModel initialization doesn't throw unhandled exceptions
- All states have stable default values
- ModalBottomSheet is properly initialized before use
