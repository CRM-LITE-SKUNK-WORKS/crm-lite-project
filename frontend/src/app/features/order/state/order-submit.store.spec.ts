import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { CATALOG } from '../../../core/i18n';
import { type OrderResponse, type SubmitOrderRequest } from '../model';
import { OrderSubmitStore } from './order-submit.store';

const BODY: SubmitOrderRequest = {
  accountNumber: '2240000015',
  serviceAddressId: 7,
  items: [{ offerId: 101, characteristics: [] }],
};

const CREATED: OrderResponse = {
  orderNumber: '2026000018',
  orderStatus: 'MIDLWARE',
  accountNumber: '2240000015',
  customerNumber: 1261000010,
  totalAmount: 175,
  items: [{ offerId: 101, productId: 5001, amount: 175 }],
};

describe('OrderSubmitStore', () => {
  let store: OrderSubmitStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        OrderSubmitStore,
        provideHttpClient(),
        provideCoreHttp(),
        provideHttpClientTesting(),
      ],
    });
    store = TestBed.inject(OrderSubmitStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts idle', () => {
    expect(store.inFlight()).toBe(false);
    expect(store.succeeded()).toBe(false);
    expect(store.locked()).toBe(false);
    expect(store.errorKey()).toBeNull();
  });

  it('keeps the 201 body and reports success (AC-SALE-01-15)', () => {
    store.submit(BODY);
    expect(store.inFlight()).toBe(true);
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/orders').flush(CREATED);

    expect(store.inFlight()).toBe(false);
    expect(store.succeeded()).toBe(true);
    expect(store.order()?.orderNumber).toBe('2026000018');
  });

  // --- Idempotency-Key (ADR-016 idempotency addendum) -----------------------
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  describe('Idempotency-Key: one UUID per logical submit attempt, reused across retries', () => {
    it('sends a UUID-shaped Idempotency-Key header on submit', () => {
      store.submit(BODY);
      const req = http.expectOne((r) => r.url === '/api/orders');
      expect(req.request.headers.get('Idempotency-Key')).toMatch(UUID_RE);
      req.flush(CREATED);
    });

    it('reuses the SAME key when the identical body is retried after a failure', () => {
      store.submit(BODY);
      const firstReq = http.expectOne((r) => r.url === '/api/orders');
      const firstKey = firstReq.request.headers.get('Idempotency-Key');
      firstReq.flush({ messageKey: 'MSG-SALE-SUBMIT-UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });

      // Same click target, same basket: this IS a retry of the same attempt —
      // the backend's own replay protection depends on the key matching.
      store.submit(BODY);
      const secondReq = http.expectOne((r) => r.url === '/api/orders');
      expect(secondReq.request.headers.get('Idempotency-Key')).toBe(firstKey);
      secondReq.flush(CREATED);
    });

    it('mints a NEW key when the body changed since the last attempt', () => {
      store.submit(BODY);
      const firstReq = http.expectOne((r) => r.url === '/api/orders');
      const firstKey = firstReq.request.headers.get('Idempotency-Key');
      firstReq.flush({ messageKey: 'MSG-SALE-DUP-OFFER' }, { status: 400, statusText: 'Bad Request' });

      // The user edited the basket before submitting again — a genuinely
      // different sale, so reusing the old key would hit the backend's
      // same-key-different-payload 409 instead of submitting the edit.
      const editedBody: SubmitOrderRequest = { ...BODY, serviceAddressId: 8 };
      store.submit(editedBody);
      const secondReq = http.expectOne((r) => r.url === '/api/orders');
      const secondKey = secondReq.request.headers.get('Idempotency-Key');
      expect(secondKey).not.toBe(firstKey);
      expect(secondKey).toMatch(UUID_RE);
      secondReq.flush(CREATED);
    });
  });

  // --- The duplicate-submit guard (ADR-016 §5.3b) ---------------------------
  describe('duplicate-submit guard — POST /api/orders is NOT idempotent', () => {
    it('ignores a second submit while the first is in flight', () => {
      store.submit(BODY);
      store.submit(BODY);
      store.submit(BODY);

      // One request, not three: a second POST would create a SECOND set of
      // products and orphan the first.
      const open = http.match((r) => r.url === '/api/orders');
      expect(open.length).toBe(1);
      open[0]?.flush(CREATED);
    });

    it('never submits again once the order exists', () => {
      store.submit(BODY);
      http.expectOne((r) => r.url === '/api/orders').flush(CREATED);

      expect(store.locked()).toBe(true);
      store.submit(BODY);
      http.expectNone((r) => r.url === '/api/orders');
    });

    it('unlocks after a FAILED submit so a legitimate retry is possible', () => {
      store.submit(BODY);
      http
        .expectOne((r) => r.url === '/api/orders')
        .flush({ messageKey: 'MSG-SALE-DUP-OFFER' }, { status: 400, statusText: 'Bad Request' });

      // The sale was compensated server-side, so retrying is correct here.
      expect(store.locked()).toBe(false);
      store.submit(BODY);
      http.expectOne((r) => r.url === '/api/orders').flush(CREATED);
      expect(store.succeeded()).toBe(true);
    });
  });

  // --- Error mapping ---------------------------------------------------------
  describe('errorKey', () => {
    function failWith(status: number, body: object | string): void {
      store.submit(BODY);
      http
        .expectOne((r) => r.url === '/api/orders')
        .flush(body, { status, statusText: 'Error' });
    }

    it("renders the backend's own messageKey whenever the catalogue has it", () => {
      failWith(400, { messageKey: 'MSG-SALE-MULTI-INTERNET' });
      expect(store.errorKey()).toBe('MSG-SALE-MULTI-INTERNET');
    });

    it('surfaces per-field rejections alongside the banner (AC-SALE-01-19)', () => {
      failWith(400, {
        messageKey: 'MSG-VALIDATION-ERROR',
        validationErrors: { 'items[0].characteristics[0].value': 'MSG-VAL-CHAR-REQUIRED' },
      });
      expect(store.errorKey()).toBe('MSG-VALIDATION-ERROR');
      expect(store.fieldErrorKeys()).toEqual(['MSG-VAL-CHAR-REQUIRED']);
    });

    // The product write path now rejects a serviceAddressId that is not the
    // customer's (400) and reports customer-service unreachable (503). The key
    // it uses could not be verified from the code (that commit is not merged
    // here), so these two assert the STATUS fallback, which holds either way.
    it('falls back by STATUS for an un-catalogued 400 (e.g. address not the customer’s)', () => {
      failWith(400, { messageKey: 'MSG-SOMETHING-NOT-IN-THE-CATALOGUE' });
      expect(store.errorKey()).toBe('MSG-VALIDATION-ERROR');
    });

    it('falls back by STATUS for an un-catalogued 503 (e.g. customer-service down)', () => {
      failWith(503, { messageKey: 'MSG-SOME-NEW-UPSTREAM-KEY' });
      expect(store.errorKey()).toBe('MSG-SALE-SUBMIT-UNAVAILABLE');
    });

    it('maps a body-less 503 to the same key (no envelope at all)', () => {
      failWith(503, 'Service Unavailable');
      expect(store.errorKey()).toBe('MSG-SALE-SUBMIT-UNAVAILABLE');
    });

    it('every key it can produce is renderable', () => {
      for (const key of [
        'MSG-VALIDATION-ERROR',
        'MSG-SALE-SUBMIT-UNAVAILABLE',
        'MSG-INTERNAL-ERROR',
      ]) {
        expect(CATALOG).toHaveProperty(key);
      }
    });

    it('clears on the next submit attempt', () => {
      failWith(400, { messageKey: 'MSG-SALE-DUP-OFFER' });
      expect(store.errorKey()).toBe('MSG-SALE-DUP-OFFER');

      store.submit(BODY);
      expect(store.errorKey()).toBeNull();
      http.expectOne((r) => r.url === '/api/orders').flush(CREATED);
    });
  });
});
