/**
 * security-utils.ts
 * 🥛 Production-Grade Input Validation and Sanitization Utilities
 */

/**
 * Escapes HTML special characters to prevent Reflected/Stored XSS and HTML Injection.
 */
export function escapeHTML(str: string): string {
  if (typeof str !== 'string') return '';
  return str.replace(/[&<>"']/g, (match) => {
    switch (match) {
      case '&': return '&amp;';
      case '<': return '&lt;';
      case '>': return '&gt;';
      case '"': return '&quot;';
      case "'": return '&#x27;';
      default: return match;
    }
  });
}

/**
 * Validates if a URL is safe to render or redirect to.
 * Strictly enforces HTTPS, blocks javascript:, data:, file:, and content: schemes,
 * and matches the host against approved domains to prevent Open Redirects / URL injection.
 */
export function validateURL(urlStr: string, restrictToApprovedDomains = true): boolean {
  if (!urlStr) return false;
  
  // Quick pre-validation to block obvious dangerous schemes
  const lowerUrl = urlStr.trim().toLowerCase();
  if (
    lowerUrl.startsWith('javascript:') ||
    lowerUrl.startsWith('data:') ||
    lowerUrl.startsWith('file:') ||
    lowerUrl.startsWith('content:') ||
    lowerUrl.includes('\\')
  ) {
    return false;
  }

  try {
    const parsed = new URL(urlStr);
    
    // Enforce HTTPS
    if (parsed.protocol !== 'https:') {
      return false;
    }

    if (restrictToApprovedDomains) {
      const approvedDomains = [
        'rawmilk.in',
        'www.rawmilk.in',
        'raw-milk-1e36d.firebaseapp.com',
        'raw-milk-1e36d.web.app'
      ];
      
      // Ensure hostname matches one of the approved domains exactly
      return approvedDomains.includes(parsed.hostname);
    }

    return true;
  } catch (e) {
    return false;
  }
}

/**
 * Sanitizes user input before placing it in the DOM or dangerouslySetInnerHTML.
 * Strip script tags and HTML element structures.
 */
export function sanitizeInput(input: string): string {
  if (typeof input !== 'string') return '';
  
  // 1. First escape HTML entities
  const escaped = escapeHTML(input);

  // 2. Double-safety check: strip out any residual tags
  return escaped.replace(/<[^>]*>?/gm, '');
}

/**
 * Strict regex validation for common fields.
 */
export const SecurityValidators = {
  // Letters, numbers, spaces, and basic punctuation
  name: (val: string) => /^[a-zA-Z\s.\-']{2,100}$/.test(val),
  
  // Standard email validation regex (RFC 5322)
  email: (val: string) => /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/.test(val),
  
  // Phone number (10 to 15 digits, optional leading +)
  phone: (val: string) => /^\+?[0-9]{10,15}$/.test(val),
  
  // Alphanumeric, spaces, commas, periods, hyphens (safe for addresses)
  address: (val: string) => /^[a-zA-Z0-9\s,.\-#/()]{5,250}$/.test(val)
};
