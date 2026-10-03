'use strict';

(function (root, factory) {
  const api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root) root.OmegasPortmonFrameDecoder = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const bytes = hex => String(hex || '').trim().split(/\s+/).filter(Boolean).map(value => Number.parseInt(value, 16));
  const checksum = values => values.reduce((sum, value) => (sum + value) & 0xFF, 0);

  function decodeEnvelope(requestHex, responseHex) {
    const request = bytes(requestHex);
    const response = bytes(responseHex);
    const echoed = response.length >= request.length && request.every((value, index) => response[index] === value);
    if (!echoed || response.length <= request.length) {
      return { valid:false, echoed:false, status:null, payload:[], checksumValid:false, reason:'response-does-not-echo-request' };
    }
    const body = response.slice(request.length);
    const receivedChecksum = body.at(-1);
    const checksumValid = body.length >= 2 && checksum(body.slice(0, -1)) === receivedChecksum;
    return {
      valid: checksumValid,
      echoed: true,
      status: body[0],
      statusHex: body[0].toString(16).padStart(2, '0').toUpperCase(),
      payload: body.slice(1, -1),
      receivedChecksum,
      calculatedChecksum: checksum(body.slice(0, -1)),
      checksumValid,
    };
  }

  return { bytes, checksum, decodeEnvelope };
}));
