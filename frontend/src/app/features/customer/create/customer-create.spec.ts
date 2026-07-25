import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideCoreHttp } from '../../../core/http';
import { CustomerFlashService } from '../state/customer-flash.service';
import { CustomerCreate } from './customer-create';

/**
 * Create Customer wizard spec (mock §6.3; FR-CUST-03, KR-10, ADR-003). House
 * style: data-testid selection, rendered copy, HTTP faked at the boundary —
 * the ONE atomic POST is asserted body-exact; the address step must never
 * touch the network beyond the lookup cascade.
 */
describe('CustomerCreate', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [CustomerCreate],
      providers: [provideCoreHttp(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(): ComponentFixture<CustomerCreate> {
    const fixture = TestBed.createComponent(CustomerCreate);
    fixture.detectChanges();
    return fixture;
  }

  function byTestId(fixture: ComponentFixture<CustomerCreate>, id: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  }

  function click(fixture: ComponentFixture<CustomerCreate>, id: string): void {
    (byTestId(fixture, id) as HTMLButtonElement).click();
    fixture.detectChanges();
    fixture.detectChanges(); // effects → CVA signals → DOM (zoneless TestBed)
  }

  function type(fixture: ComponentFixture<CustomerCreate>, testId: string, value: string): void {
    const inputEl = byTestId(fixture, testId) as HTMLInputElement;
    inputEl.value = value;
    inputEl.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function activeStep(fixture: ComponentFixture<CustomerCreate>): string | null {
    return (
      (fixture.nativeElement as HTMLElement)
        .querySelector('[aria-current="step"]')
        ?.getAttribute('data-testid') ?? null
    );
  }

  /** Step 1 with valid mock-rule data (§6.3). */
  function fillDemographic(fixture: ComponentFixture<CustomerCreate>): void {
    type(fixture, 'customer-create-field-first-name-input', 'Velihan');
    type(fixture, 'customer-create-field-last-name-input', 'Gözek');
    type(fixture, 'customer-create-field-birth-date-picker', '15.03.1992');
    click(fixture, 'customer-create-field-gender-select');
    click(fixture, 'customer-create-field-gender-select-option-Male');
    type(fixture, 'customer-create-field-nationality-id-input', '10000000004');
  }

  /** Step 2: one draft address through the SHARED dialog (lookup cascade only).
   *  `expectLookups=false` on repeat calls — the root lookup cache never
   *  re-fetches (FE-ADR-006 §6), so no request leaves the second time. */
  function addDraftAddress(fixture: ComponentFixture<CustomerCreate>, expectLookups = true): void {
    click(fixture, 'customer-create-address-add-button');
    if (expectLookups) {
      http.expectOne('/api/cities').flush([{ cityId: 1, name: 'Istanbul' }]);
      fixture.detectChanges();
    }
    click(fixture, 'customer-create-address-dialog-city-select');
    click(fixture, 'customer-create-address-dialog-city-select-option-1');
    if (expectLookups) {
      http.expectOne('/api/cities/1/districts').flush([{ districtId: 1, name: 'Kadıköy' }]);
      fixture.detectChanges();
    }
    click(fixture, 'customer-create-address-dialog-district-select');
    click(fixture, 'customer-create-address-dialog-district-select-option-1');
    type(fixture, 'customer-create-address-dialog-street-input', 'Example Street');
    type(fixture, 'customer-create-address-dialog-house-no-input', '10/2');
    type(fixture, 'customer-create-address-dialog-description-input', 'Home');
    click(fixture, 'customer-create-address-dialog-save-button');
  }

  function goToContact(fixture: ComponentFixture<CustomerCreate>): void {
    fillDemographic(fixture);
    click(fixture, 'customer-create-next-button');
    addDraftAddress(fixture);
    click(fixture, 'customer-create-next-button');
    type(fixture, 'customer-create-field-email-input', 'velihan@example.com');
    type(fixture, 'customer-create-field-mobile-input', '05321112233');
  }

  function submit(fixture: ComponentFixture<CustomerCreate>): void {
    click(fixture, 'customer-create-submit-button');
  }

  const EXPECTED_BODY = {
    demographic: {
      firstName: 'Velihan',
      middleName: null,
      lastName: 'Gözek',
      fatherName: null,
      motherName: null,
      birthDate: '1992-03-15',
      gender: 'Male',
      nationalityId: '10000000004',
    },
    addresses: [
      {
        cityId: 1,
        districtId: 1,
        street: 'Example Street',
        houseFlatNumber: '10/2',
        addressDescription: 'Home',
        primary: true,
      },
    ],
    contactMedium: {
      email: 'velihan@example.com',
      mobilePhone: '05321112233',
      homePhone: null,
      fax: null,
    },
  };

  function apiError(status: number, messageKey: string, validationErrors?: Record<string, string>) {
    return {
      body: {
        timestamp: '',
        status,
        error: 'x',
        messageKey,
        message: 'raw',
        path: '/api/customers',
        validationErrors: validationErrors ?? null,
      },
      opts: { status, statusText: 'Error' },
    };
  }

  // ---- step gating + client validation (VR-* as UX, FE-ADR-007) ------------

  it('step 1 gates Next on required fields and blocks an invalid Nationality ID with MSG-VAL-NATID', () => {
    const fixture = render();
    const next = byTestId(fixture, 'customer-create-next-button') as HTMLButtonElement;
    expect(next.disabled).toBe(true);
    fillDemographic(fixture);
    type(fixture, 'customer-create-field-nationality-id-input', '123'); // break it
    expect(next.disabled).toBe(false); // filled, but invalid — checked on Next
    click(fixture, 'customer-create-next-button');
    expect(activeStep(fixture)).toBe('customer-create-stepper-step-demographic'); // stayed
    expect(byTestId(fixture, 'customer-create-field-nationality-id-error')?.textContent).toContain(
      '11 digits',
    );
    type(fixture, 'customer-create-field-nationality-id-input', '10000000004'); // typing clears
    expect(byTestId(fixture, 'customer-create-field-nationality-id-error')?.textContent?.trim()).toBe('');
    click(fixture, 'customer-create-next-button');
    expect(activeStep(fixture)).toBe('customer-create-stepper-step-address');
  });

  it('step 2 requires ≥1 address; drafts are collected via the SHARED dialog with NO address API call', () => {
    const fixture = render();
    fillDemographic(fixture);
    click(fixture, 'customer-create-next-button');
    const next = byTestId(fixture, 'customer-create-next-button') as HTMLButtonElement;
    expect(next.disabled).toBe(true); // no address yet
    addDraftAddress(fixture);
    expect(byTestId(fixture, 'customer-create-address-1')?.textContent).toContain(
      'Kadıköy, Istanbul', // names resolved from the lookup cache, display only
    );
    expect(byTestId(fixture, 'customer-create-address-1')?.textContent).toContain('Primary');
    expect(http.match((r) => r.url.includes('/addresses')).length).toBe(0); // atomic — no partial write
    expect(next.disabled).toBe(false);
  });

  it('keeps EXACTLY one primary across drafts and deletes a draft locally after confirmation', () => {
    const fixture = render();
    fillDemographic(fixture);
    click(fixture, 'customer-create-next-button');
    addDraftAddress(fixture);
    addDraftAddress(fixture, false); // second draft — cascade cached, no new requests
    expect(byTestId(fixture, 'customer-create-address-2-set-primary')).not.toBeNull();
    click(fixture, 'customer-create-address-2-set-primary');
    expect(byTestId(fixture, 'customer-create-address-2')?.textContent).toContain('Primary');
    expect(byTestId(fixture, 'customer-create-address-1-set-primary')).not.toBeNull(); // demoted
    click(fixture, 'customer-create-address-1-delete'); // non-primary now deletable
    click(fixture, 'customer-create-address-delete-dialog-confirm-button');
    expect(byTestId(fixture, 'customer-create-address-1')).toBeNull();
    expect(http.match((r) => r.method === 'DELETE').length).toBe(0); // local removal only
  });

  // ---- the atomic POST -----------------------------------------------------

  it('creates with ONE atomic POST carrying the exact nested body, then flashes onto Customer Info', () => {
    const fixture = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    goToContact(fixture);
    submit(fixture);
    const post = http.expectOne('/api/customers');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual(EXPECTED_BODY);
    post.flush(
      { customerNumber: 1004, firstName: 'Velihan', role: 'Customer', status: 'ACTV' },
      { status: 201, statusText: 'Created' },
    );
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith(['/customers', 1004]);
    expect(TestBed.inject(CustomerFlashService).consume()).toBe('UI-CREATE-TOAST-SUCCESS');
  });

  it('guards against double submission: a second click while pending sends nothing', () => {
    const fixture = render();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    goToContact(fixture);
    submit(fixture);
    submit(fixture); // busy → swallowed
    const posts = http.match('/api/customers');
    expect(posts.length).toBe(1);
    posts[0].flush({ customerNumber: 1004 }, { status: 201, statusText: 'Created' });
  });

  it('client VR-MOBILE gates Create: wrong prefix shows MSG-VAL-PHONE, no request leaves', () => {
    const fixture = render();
    goToContact(fixture);
    type(fixture, 'customer-create-field-mobile-input', '02121112233');
    submit(fixture);
    expect(http.match('/api/customers').length).toBe(0);
    expect(byTestId(fixture, 'customer-create-field-mobile-error')?.textContent).toContain(
      'valid phone number',
    );
  });

  // ---- backend answers (the real authority) --------------------------------

  it('400 validationErrors land on their fields and the wizard jumps to the owning step', () => {
    const fixture = render();
    goToContact(fixture);
    submit(fixture);
    const rejection = apiError(400, 'MSG-VALIDATION-ERROR', {
      'demographic.firstName': 'MSG-VAL-NAME',
      'contactMedium.email': 'MSG-VAL-EMAIL',
    });
    http.expectOne('/api/customers').flush(rejection.body, rejection.opts);
    fixture.detectChanges();
    expect(activeStep(fixture)).toBe('customer-create-stepper-step-demographic'); // owning step
    expect(byTestId(fixture, 'customer-create-error-banner')?.textContent).toContain(
      'Some fields are invalid.',
    );
    expect(byTestId(fixture, 'customer-create-field-first-name-error')?.textContent).toContain(
      'valid name',
    );
  });

  it('409 duplicate Nationality ID (ADR-003, soft-deleted included) binds to the field on step 1', () => {
    const fixture = render();
    goToContact(fixture);
    submit(fixture);
    const dup = apiError(409, 'MSG-CUST-DUP-NATID');
    http.expectOne('/api/customers').flush(dup.body, dup.opts);
    fixture.detectChanges();
    expect(activeStep(fixture)).toBe('customer-create-stepper-step-demographic');
    expect(byTestId(fixture, 'customer-create-error-banner')?.textContent).toContain(
      'already exists with this Nationality ID',
    );
    expect(byTestId(fixture, 'customer-create-field-nationality-id-error')?.textContent).toContain(
      'already exists',
    );
    expect(byTestId(fixture, 'customer-create-error-banner')?.textContent).not.toContain('raw');
  });

  it('MERNIS rejection (KR-10: verified INSIDE the POST) shows MSG-CUST-NATID-VERIFICATION-FAILED', () => {
    const fixture = render();
    goToContact(fixture);
    submit(fixture);
    const mernis = apiError(400, 'MSG-CUST-NATID-VERIFICATION-FAILED');
    http.expectOne('/api/customers').flush(mernis.body, mernis.opts);
    fixture.detectChanges();
    expect(activeStep(fixture)).toBe('customer-create-stepper-step-demographic');
    expect(byTestId(fixture, 'customer-create-error-banner')?.textContent).toContain(
      'could not be verified',
    );
  });

  it('503 MERNIS outage: banner only, data preserved, Create is retryable (nothing persisted)', () => {
    const fixture = render();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    goToContact(fixture);
    submit(fixture);
    const outage = apiError(503, 'MSG-MERNIS-UNAVAILABLE');
    http.expectOne('/api/customers').flush(outage.body, outage.opts);
    fixture.detectChanges();
    expect(activeStep(fixture)).toBe('customer-create-stepper-step-contact'); // stays put
    expect(byTestId(fixture, 'customer-create-error-banner')?.textContent).toContain(
      'verification service is currently unavailable',
    );
    expect(
      (byTestId(fixture, 'customer-create-field-email-input') as HTMLInputElement).value,
    ).toBe('velihan@example.com'); // no data loss
    submit(fixture); // retry issues a fresh POST
    http.expectOne('/api/customers').flush(
      { customerNumber: 1005 },
      { status: 201, statusText: 'Created' },
    );
  });
});
