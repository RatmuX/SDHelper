//! Data Protection API: encrypts a small secret so that only the same Windows user can decrypt it.

use super::failed;
use crate::backend::{BackendError, BackendResult};
use windows::core::w;
use windows::Win32::Foundation::{LocalFree, HLOCAL};
use windows::Win32::Security::Cryptography::{
    CryptProtectData, CryptUnprotectData, CRYPTPROTECT_UI_FORBIDDEN, CRYPT_INTEGER_BLOB,
};

/// Extra input to the key derivation. It is not a secret; it makes a blob protected by SDHelper useless to
/// another program that merely calls `CryptUnprotectData` without knowing this value.
const ENTROPY: &[u8] = b"SDHelper agent secret v1";

fn blob(bytes: &[u8]) -> CRYPT_INTEGER_BLOB {
    CRYPT_INTEGER_BLOB {
        cbData: bytes.len() as u32,
        pbData: bytes.as_ptr() as *mut u8,
    }
}

/// Copies the bytes of a blob that Windows allocated, and frees it.
fn take(output: CRYPT_INTEGER_BLOB) -> Vec<u8> {
    // SAFETY: on success `pbData` points to `cbData` bytes allocated with LocalAlloc.
    let bytes =
        unsafe { std::slice::from_raw_parts(output.pbData, output.cbData as usize) }.to_vec();
    unsafe {
        let _ = LocalFree(Some(HLOCAL(output.pbData.cast())));
    }
    bytes
}

pub fn protect(data: &[u8]) -> BackendResult<Vec<u8>> {
    let (input, entropy) = (blob(data), blob(ENTROPY));
    let mut output = CRYPT_INTEGER_BLOB::default();
    // SAFETY: all pointers refer to live blobs for the duration of the call; the flag forbids any UI.
    unsafe {
        CryptProtectData(
            &input,
            w!("SDHelper"),
            Some(&entropy),
            None,
            None,
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut output,
        )
    }
    .map_err(failed)?;
    Ok(take(output))
}

pub fn unprotect(data: &[u8]) -> BackendResult<Vec<u8>> {
    if data.is_empty() {
        return Err(BackendError::Failed("nothing to decrypt".into()));
    }
    let (input, entropy) = (blob(data), blob(ENTROPY));
    let mut output = CRYPT_INTEGER_BLOB::default();
    // SAFETY: as in `protect`.
    unsafe {
        CryptUnprotectData(
            &input,
            None,
            Some(&entropy),
            None,
            None,
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut output,
        )
    }
    .map_err(failed)?;
    Ok(take(output))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_secret_survives_a_round_trip_and_is_not_stored_in_clear() {
        let secret = b"correct horse battery staple";
        let blob = protect(secret).expect("protect");
        assert_ne!(blob, secret);
        assert!(!blob.windows(secret.len()).any(|w| w == secret));
        assert_eq!(unprotect(&blob).expect("unprotect"), secret);
    }

    #[test]
    fn a_tampered_or_foreign_blob_is_rejected_without_panicking() {
        let mut blob = protect(b"secret").unwrap();
        let last = blob.len() - 1;
        blob[last] ^= 0xFF;
        assert!(unprotect(&blob).is_err());
        assert!(unprotect(b"definitely not a dpapi blob").is_err());
        assert!(unprotect(&[]).is_err());
    }

    #[test]
    fn two_protections_of_the_same_secret_differ() {
        let first = protect(b"secret").unwrap();
        let second = protect(b"secret").unwrap();
        assert_ne!(first, second, "each blob carries fresh randomness");
    }
}
