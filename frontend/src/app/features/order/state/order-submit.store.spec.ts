import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideCoreHttp } from '../../../core/http';
import { CATALOG } from '../../../core/i18n';
import { type OrderStatusResponse, type SubmitDraftRequest } from '../model';
import { OrderSubmitStore } from './order-submit.store';

const ACCOUNT_NUMBER = '2240000015';
const ORDER_NUMBER = '2026000018';

const BODY: SubmitDraftRequest = {
  serviceAddressId: 7,
  items: [{ offerId: 101, characteristics: [] }],
};

const DRAFT: OrderStatusResponse = {
  orderNumber: ORDER_NUMBER,
  orderStatus: 'WAIT',
  processingStatus: 'DRAFT',
  failureMessageKey: null,
  updatedAt: '2026-08-10T10:00:00Z',
};

const ACCEPTED: OrderStatusResponse = {
  orderNumber: ORDER_NUMBER,
  orderStatus: 'MIDLWARE',
  processingStatus: 'PROCESSING',
  failureMessageKey: null,
  updatedAt: '2026-08-10T10:00:01Z',
};

const COMPLETED: OrderStatusResponse = {
  ...ACCEPTED,
  processingStatus: 'COMPLETED',
  updatedAt: '2026-08-10T10:00:05Z',
};

const FAILED: OrderStatusResponse = {
  ...ACCEPTED,
  processingStatus: 'FAILED',
  failureMessageKey: 'MSG-SALE-FAILED',
  updatedAt: '2026-08-10T10:00:05Z',
};

describe('OrderSubmitStore', () => {
  let store: OrderSubmitStore;
  let http: HttpTestingController;

  beforeEach(() => {
    // Fake timers throughout: `submit()`'s 202 immediately arms a `setTimeout`
    // poll (order-submit.store.ts's `POLL_INTERVAL_MS`), and a real interval
    // would make every test that reaches acceptance wait on it for real.
    vi.useFakeTimers();
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
  afterEach(() => vi.useRealTimers());

  /** The store's own polling interval — advancing by this much makes the
   *  next scheduled poll (or the first one, right after acceptance) fire. */
  const POLL_INTERVAL_MS = 2_000;

  /** Settles the poll `startPolling` opened after a 202, with a TERMINAL
   *  status — used by every test that accepts a submit but does not care
   *  about polling behaviour itself, so it does not leave a request open. */
  function resolvePoll(status: OrderStatusResponse = COMPLETED): void {
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(status, {
      status: 200,
      statusText: 'OK',
    });
  }

  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  function flushDraft(response: OrderStatusResponse = DRAFT): void {
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/orders/drafts').flush(response, {
      status: 201,
      statusText: 'Created',
    });
  }

  function startAndFlushDraft(response: OrderStatusResponse = DRAFT): void {
    store.startDraft(ACCOUNT_NUMBER);
    flushDraft(response);
  }

  // --- The legacy synchronous contract must never be touched ----------------
  it('never calls the deprecated POST /api/orders', () => {
    startAndFlushDraft();
    store.submit(BODY);
    http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(ACCEPTED, {
      status: 202,
      statusText: 'Accepted',
    });
    http.expectNone((r) => r.method === 'POST' && r.url === '/api/orders');
    resolvePoll();
  });

  // ---- Draft (Start New Sale, ADR-018 §6) ------------------------------------
  describe('startDraft', () => {
    it('starts idle', () => {
      expect(store.draftPending()).toBe(false);
      expect(store.orderNumber()).toBeNull();
      expect(store.locked()).toBe(false);
    });

    it('creates exactly one WAIT draft and stores its KR-12 number', () => {
      store.startDraft(ACCOUNT_NUMBER);
      expect(store.draftPending()).toBe(true);
      flushDraft();

      expect(store.draftPending()).toBe(false);
      expect(store.orderNumber()).toBe(ORDER_NUMBER);
    });

    it('sends a UUID-shaped Idempotency-Key header, body { accountNumber }', () => {
      store.startDraft(ACCOUNT_NUMBER);
      const req = http.expectOne((r) => r.url === '/api/orders/drafts');
      expect(req.request.headers.get('Idempotency-Key')).toMatch(UUID_RE);
      expect(req.request.body).toEqual({ accountNumber: ACCOUNT_NUMBER });
      req.flush(DRAFT, { status: 201, statusText: 'Created' });
    });

    it('a re-run effect never creates a second draft — in flight', () => {
      store.startDraft(ACCOUNT_NUMBER);
      store.startDraft(ACCOUNT_NUMBER);
      store.startDraft(ACCOUNT_NUMBER);

      const open = http.match((r) => r.url === '/api/orders/drafts');
      expect(open.length).toBe(1);
      open[0]?.flush(DRAFT, { status: 201, statusText: 'Created' });
    });

    it('a re-run effect never creates a second draft — already succeeded', () => {
      startAndFlushDraft();
      store.startDraft(ACCOUNT_NUMBER);
      http.expectNone((r) => r.url === '/api/orders/drafts');
    });

    it('draft creation failure leaves no order number and never fabricates one', () => {
      store.startDraft(ACCOUNT_NUMBER);
      http
        .expectOne((r) => r.url === '/api/orders/drafts')
        .flush({ messageKey: 'MSG-ACCT-NOT-ACTIVE' }, { status: 409, statusText: 'Conflict' });

      expect(store.orderNumber()).toBeNull();
      expect(store.draftPending()).toBe(false);
      expect(store.draftErrorKey()).toBe('MSG-ACCT-NOT-ACTIVE');
    });

    it('retrying after a failed draft reuses the SAME Idempotency-Key', () => {
      store.startDraft(ACCOUNT_NUMBER);
      const first = http.expectOne((r) => r.url === '/api/orders/drafts');
      const firstKey = first.request.headers.get('Idempotency-Key');
      first.flush({ messageKey: 'MSG-SERVICE-UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });

      store.startDraft(ACCOUNT_NUMBER);
      const second = http.expectOne((r) => r.url === '/api/orders/drafts');
      expect(second.request.headers.get('Idempotency-Key')).toBe(firstKey);
      second.flush(DRAFT, { status: 201, statusText: 'Created' });
      expect(store.orderNumber()).toBe(ORDER_NUMBER);
    });

    it('every key draftErrorKey can produce is renderable', () => {
      for (const key of ['MSG-VALIDATION-ERROR', 'MSG-SALE-SUBMIT-UNAVAILABLE', 'MSG-INTERNAL-ERROR']) {
        expect(CATALOG).toHaveProperty(key);
      }
    });
  });

  // ---- Cancel before Submit (abandon, ADR-018 §6) ----------------------------
  describe('abandonDraft', () => {
    it('calls DELETE .../draft for the current WAIT order', () => {
      startAndFlushDraft();
      store.abandonDraft();
      http.expectOne((r) => r.method === 'DELETE' && r.url === `/api/orders/${ORDER_NUMBER}/draft`)
        .flush(null, { status: 204, statusText: 'No Content' });
    });

    it('is a no-op when no draft exists yet', () => {
      store.abandonDraft();
      http.expectNone((r) => r.method === 'DELETE');
    });

    it('is a no-op once the sale has been accepted — never pretends to cancel the saga', () => {
      startAndFlushDraft();
      store.submit(BODY);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(ACCEPTED, {
        status: 202,
        statusText: 'Accepted',
      });

      store.abandonDraft();
      http.expectNone((r) => r.method === 'DELETE');
    });

    it('does not block on a failed abandon — a WAIT draft can never auto-start regardless', () => {
      startAndFlushDraft();
      store.abandonDraft();
      // The store never retries or falls back — it fires once and moves on.
      http
        .expectOne((r) => r.method === 'DELETE')
        .flush({ messageKey: 'MSG-SERVICE-UNAVAILABLE' }, { status: 503, statusText: 'Unavailable' });
    });
  });

  // ---- Submit (Confirm Submit, ADR-018 §6) -----------------------------------
  describe('submit', () => {
    it('refuses to submit before a draft exists', () => {
      store.submit(BODY);
      http.expectNone((r) => r.url.endsWith('/submit'));
    });

    it('posts to /api/orders/{orderNumber}/submit with a UUID Idempotency-Key, body WITHOUT accountNumber', () => {
      startAndFlushDraft();
      store.submit(BODY);
      const req = http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`);
      expect(req.request.headers.get('Idempotency-Key')).toMatch(UUID_RE);
      expect(req.request.body).toEqual(BODY);
      expect(req.request.body).not.toHaveProperty('accountNumber');
      req.flush(ACCEPTED, { status: 202, statusText: 'Accepted' });
      resolvePoll();
    });

    it('the submit Idempotency-Key is INDEPENDENT of the draft one', () => {
      store.startDraft(ACCOUNT_NUMBER);
      const draftReq = http.expectOne((r) => r.url === '/api/orders/drafts');
      const draftKey = draftReq.request.headers.get('Idempotency-Key');
      draftReq.flush(DRAFT, { status: 201, statusText: 'Created' });

      store.submit(BODY);
      const submitReq = http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`);
      expect(submitReq.request.headers.get('Idempotency-Key')).not.toBe(draftKey);
      submitReq.flush(ACCEPTED, { status: 202, statusText: 'Accepted' });
      resolvePoll();
    });

    it('202 acceptance flips the state and starts processing', () => {
      startAndFlushDraft();
      store.submit(BODY);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(ACCEPTED, {
        status: 202,
        statusText: 'Accepted',
      });

      expect(store.accepted()).toBe(true);
      expect(store.processingStatus()).toBe('PROCESSING');
      expect(store.succeeded()).toBe(false);
      expect(store.failed()).toBe(false);

      // A poll is armed behind the 202 — settle it so `afterEach`'s
      // `http.verify()` does not complain about a request left open.
      resolvePoll();
    });

    describe('duplicate-submit guard', () => {
      it('ignores a second submit while the first is in flight', () => {
        startAndFlushDraft();
        store.submit(BODY);
        store.submit(BODY);
        store.submit(BODY);

        const open = http.match((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`);
        expect(open.length).toBe(1);
        open[0]?.flush(ACCEPTED, { status: 202, statusText: 'Accepted' });
        resolvePoll();
      });

      it('never submits again once accepted', () => {
        startAndFlushDraft();
        store.submit(BODY);
        http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(ACCEPTED, {
          status: 202,
          statusText: 'Accepted',
        });
        resolvePoll();

        expect(store.locked()).toBe(true);
        store.submit(BODY);
        http.expectNone((r) => r.url.endsWith('/submit'));
      });

      it('unlocks after a rejected submit so a legitimate retry is possible', () => {
        startAndFlushDraft();
        store.submit(BODY);
        http
          .expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`)
          .flush({ messageKey: 'MSG-SALE-DUP-OFFER' }, { status: 400, statusText: 'Bad Request' });

        expect(store.locked()).toBe(false);
        expect(store.accepted()).toBe(false);
        store.submit(BODY);
        http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(ACCEPTED, {
          status: 202,
          statusText: 'Accepted',
        });
        resolvePoll();
      });
    });

    describe('submitErrorKey', () => {
      it("renders the backend's own messageKey whenever the catalogue has it", () => {
        startAndFlushDraft();
        store.submit(BODY);
        http
          .expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`)
          .flush({ messageKey: 'MSG-SALE-MULTI-INTERNET' }, { status: 400, statusText: 'Bad Request' });
        expect(store.submitErrorKey()).toBe('MSG-SALE-MULTI-INTERNET');
      });

      it('surfaces per-field rejections alongside the banner (AC-SALE-01-19)', () => {
        startAndFlushDraft();
        store.submit(BODY);
        http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(
          {
            messageKey: 'MSG-VALIDATION-ERROR',
            validationErrors: { 'items[0].characteristics[0].value': 'MSG-VAL-CHAR-REQUIRED' },
          },
          { status: 400, statusText: 'Bad Request' },
        );
        expect(store.submitErrorKey()).toBe('MSG-VALIDATION-ERROR');
        expect(store.fieldErrorKeys()).toEqual(['MSG-VAL-CHAR-REQUIRED']);
      });

      it('maps a body-less 503 to the unavailable fallback', () => {
        startAndFlushDraft();
        store.submit(BODY);
        http
          .expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`)
          .flush('Service Unavailable', { status: 503, statusText: 'Service Unavailable' });
        expect(store.submitErrorKey()).toBe('MSG-SALE-SUBMIT-UNAVAILABLE');
      });
    });
  });

  // ---- Status polling (ADR-018 §6, AC-SALE-01-15's processing screen) -------
  describe('polling', () => {
    function accept(): void {
      startAndFlushDraft();
      store.submit(BODY);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/submit`).flush(ACCEPTED, {
        status: 202,
        statusText: 'Accepted',
      });
    }

    it('polls the status endpoint and stops on COMPLETED', () => {
      accept();
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(COMPLETED, {
        status: 200,
        statusText: 'OK',
      });

      expect(store.succeeded()).toBe(true);

      // No further poll after the terminal state.
      vi.advanceTimersByTime(60_000);
      http.expectNone((r) => r.url.endsWith('/status'));
    });

    it('stops on FAILED and surfaces the backend failureMessageKey', () => {
      accept();
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(FAILED, {
        status: 200,
        statusText: 'OK',
      });

      expect(store.failed()).toBe(true);
      expect(store.failureMessageKey()).toBe('MSG-SALE-FAILED');

      vi.advanceTimersByTime(60_000);
      http.expectNone((r) => r.url.endsWith('/status'));
    });

    it('keeps polling while PROCESSING, one request per interval', () => {
      accept();
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(ACCEPTED, {
        status: 200,
        statusText: 'OK',
      });
      http.expectNone((r) => r.url.endsWith('/status'));

      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(COMPLETED, {
        status: 200,
        statusText: 'OK',
      });
    });

    it('a transient status-request failure is retried, bounded, and never marks the sale FAILED', () => {
      accept();
      vi.advanceTimersByTime(POLL_INTERVAL_MS);

      // Three consecutive transport failures: still PROCESSING, never FAILED.
      for (let i = 0; i < 3; i++) {
        http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(null, {
          status: 0,
          statusText: 'Unknown Error',
        });
        expect(store.failed()).toBe(false);
        expect(store.processingStatus()).toBe('PROCESSING');
        vi.advanceTimersByTime(60_000);
      }

      // It keeps trying and recovers on the next good response.
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(COMPLETED, {
        status: 200,
        statusText: 'OK',
      });
      expect(store.succeeded()).toBe(true);
    });

    it('only one polling stream is ever open at once', () => {
      accept();
      vi.advanceTimersByTime(POLL_INTERVAL_MS);
      http.expectOne((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`).flush(ACCEPTED, {
        status: 200,
        statusText: 'OK',
      });

      vi.advanceTimersByTime(10_000); // several intervals could have elapsed
      const open = http.match((r) => r.url === `/api/orders/${ORDER_NUMBER}/status`);
      expect(open.length).toBeLessThanOrEqual(1);
      open[0]?.flush(COMPLETED, { status: 200, statusText: 'OK' });
    });
  });
});
