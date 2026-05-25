#!/bin/bash
FILE="/home/saif/enclave/enclave-ui/app/src/main/java/com/enclave/app/ui/chat/ChatViewModel.kt"

# 1. Remove the data classes at the top (ChatUiState, ReplyPayload, ChatMessage)
sed -i '45,74d' "$FILE"

# 2. Fix the message caching lines (injectCache)
sed -i 's/decryptedMessagesCache\[messageId\] = \(.*\)/messageDecryptorUseCase.injectCache(messageId, \1)/g' "$FILE"

