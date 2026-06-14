package com.prodigalgal.ircs.notification.mail;

import java.util.UUID;

record MailCredential(UUID id, String username, String password) {
}
