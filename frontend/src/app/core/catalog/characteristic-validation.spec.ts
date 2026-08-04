import { FormControl } from '@angular/forms';
import type { CharacteristicResponse } from './catalog.model';
import {
  characteristicErrorKey,
  characteristicValidators,
  charFormatValidator,
  charNotPastValidator,
  charRequiredValidator,
  isBlankValue,
  matchesDataType,
  todayIso,
} from './characteristic-validation';

/** `days` from today, as ISO — so no case below hardcodes a date that would
 *  silently change meaning once the wall clock passes it. */
function isoOffsetFromToday(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * These cases are the CONTRACT with product-service's
 * `CharacteristicValidationRules.matchesDataType` — every expectation below is
 * what `new BigDecimal(v)` / `LocalDate.parse(v)` / the strict `true|false`
 * comparison do on the server. If one of them ever has to change, the backend
 * changed first and this file follows it, not the other way round.
 */
describe('matchesDataType', () => {
  it('TEXT accepts anything — the server declares no pattern for it', () => {
    expect(matchesDataType('TEXT', 'A1:B2:C3:D4:E5:F6')).toBe(true);
    expect(matchesDataType('TEXT', '???')).toBe(true);
  });

  it('NUMBER accepts what BigDecimal parses, and nothing else', () => {
    for (const value of ['0', '100', '-3', '+3', '12.5', '.5', '5.', '1e5', '1E-5']) {
      expect(matchesDataType('NUMBER', value), value).toBe(true);
    }
    // The reported defect ("hıza harf girilebiliyor") and its neighbours.
    for (const value of ['100abc', 'abc', '.', '-', '1,5', '1 000', '']) {
      expect(matchesDataType('NUMBER', value), value).toBe(false);
    }
  });

  it('BOOLEAN is strictly true/false — 1/0/yes are NOT booleans', () => {
    for (const value of ['true', 'false', 'TRUE', 'False']) {
      expect(matchesDataType('BOOLEAN', value), value).toBe(true);
    }
    // Boolean.parseBoolean would silently turn each of these into `false`;
    // the backend refuses them and so does this.
    for (const value of ['1', '0', 'yes', 'evet', 'on']) {
      expect(matchesDataType('BOOLEAN', value), value).toBe(false);
    }
  });

  it('DATE is strict ISO yyyy-MM-dd and a REAL calendar day', () => {
    expect(matchesDataType('DATE', '2026-07-23')).toBe(true);
    expect(matchesDataType('DATE', '2024-02-29')).toBe(true); // a leap year
    for (const value of ['2026-02-30', '2025-02-29', '2026-13-01', '23.07.2026', '2026-7-3']) {
      expect(matchesDataType('DATE', value), value).toBe(false);
    }
  });

  it('a year below 100 is not quietly relocated into the 1900s', () => {
    // `new Date(Date.UTC(99, 0, 1))` is 1999-01-01, which would have made this
    // date validate as its own value. It must not.
    expect(matchesDataType('DATE', '0099-01-01')).toBe(true);
    expect(matchesDataType('DATE', '0099-02-30')).toBe(false);
  });

  it('an unknown data type is rejected, never waved through', () => {
    expect(matchesDataType('CURRENCY', '5')).toBe(false);
    expect(matchesDataType('', '5')).toBe(false);
  });
});

describe('isBlankValue', () => {
  it('follows the backend isBlank(): whitespace is not a value', () => {
    expect(isBlankValue(null)).toBe(true);
    expect(isBlankValue(undefined)).toBe(true);
    expect(isBlankValue('')).toBe(true);
    expect(isBlankValue('   ')).toBe(true);
    expect(isBlankValue(' x ')).toBe(false);
  });
});

describe('validators', () => {
  it('charRequired fires on blank and whitespace only (MSG-VAL-CHAR-REQUIRED)', () => {
    const validator = charRequiredValidator();
    expect(validator(new FormControl(''))).toEqual({ charRequired: true });
    expect(validator(new FormControl('  '))).toEqual({ charRequired: true });
    expect(validator(new FormControl('8'))).toBeNull();
  });

  it('charFormat ignores blank — that is required-ness, not format', () => {
    const validator = charFormatValidator('NUMBER');
    expect(validator(new FormControl(''))).toBeNull();
    expect(validator(new FormControl('abc'))).toEqual({ charFormat: true });
    // Compared TRIMMED, exactly as the server compares it.
    expect(validator(new FormControl(' 100 '))).toBeNull();
  });

  it('an OPTIONAL characteristic gets a format validator but no required one', () => {
    const optional: CharacteristicResponse = {
      characteristicId: 13,
      name: 'Static IP',
      description: null,
      dataType: 'BOOLEAN',
      mandatory: false,
    };
    const control = new FormControl('', { validators: characteristicValidators(optional) });
    expect(control.valid).toBe(true);

    control.setValue('maybe');
    expect(control.errors).toEqual({ charFormat: true });
  });
});

/**
 * The past-date rule has NO backend counterpart (see `charNotPastValidator`), so
 * unlike the block above these cases are the frontend's own, not a mirror.
 */
describe('charNotPastValidator', () => {
  const validator = charNotPastValidator();

  it('todayIso reads the LOCAL calendar, not UTC', () => {
    const now = new Date();
    const month = `${now.getMonth() + 1}`.padStart(2, '0');
    const day = `${now.getDate()}`.padStart(2, '0');
    expect(todayIso()).toBe(`${now.getFullYear()}-${month}-${day}`);
  });

  it('rejects yesterday and anything older', () => {
    expect(validator(new FormControl(isoOffsetFromToday(-1)))).toEqual({ charDatePast: true });
    expect(validator(new FormControl('2020-01-01'))).toEqual({ charDatePast: true });
  });

  it('accepts TODAY — the boundary is inclusive', () => {
    expect(validator(new FormControl(todayIso()))).toBeNull();
  });

  it('accepts tomorrow and beyond', () => {
    expect(validator(new FormControl(isoOffsetFromToday(1)))).toBeNull();
    expect(validator(new FormControl(isoOffsetFromToday(400)))).toBeNull();
  });

  it('ignores blank — that is required-ness, not the past', () => {
    expect(validator(new FormControl(''))).toBeNull();
    expect(validator(new FormControl('   '))).toBeNull();
  });

  it('leaves a malformed value to charFormat, so one field never shows two errors', () => {
    expect(validator(new FormControl('99.99.2020'))).toBeNull();
    expect(validator(new FormControl('2020-02-30'))).toBeNull();
  });

  it('is attached to DATE characteristics only', () => {
    const dateField: CharacteristicResponse = {
      characteristicId: 4,
      name: 'Commitment End Date',
      description: null,
      dataType: 'DATE',
      mandatory: false,
    };
    const control = new FormControl(isoOffsetFromToday(-1), {
      validators: characteristicValidators(dateField),
    });
    expect(control.errors).toEqual({ charDatePast: true });

    // A TEXT field holding the same string is untouched by the rule.
    const textControl = new FormControl(isoOffsetFromToday(-1), {
      validators: characteristicValidators({ ...dateField, dataType: 'TEXT' }),
    });
    expect(textControl.valid).toBe(true);
  });
});

describe('characteristicErrorKey', () => {
  it('maps to the analyst keys, required winning over format', () => {
    expect(characteristicErrorKey(null)).toBeNull();
    expect(characteristicErrorKey({ charRequired: true })).toBe('MSG-VAL-CHAR-REQUIRED');
    expect(characteristicErrorKey({ charFormat: true })).toBe('MSG-VAL-CHAR-FORMAT');
    // DatePicker raises its OWN error for half-typed text; the user must still
    // be told the value does not fit the field's type.
    expect(characteristicErrorKey({ invalidDate: true })).toBe('MSG-VAL-CHAR-FORMAT');
  });

  it('answers the past-date rule with a UI-* key — no backend sends an MSG for it', () => {
    expect(characteristicErrorKey({ charDatePast: true })).toBe('UI-VAL-CHAR-DATE-PAST');
    // Required still wins: an empty field is missing before it is anything else.
    expect(characteristicErrorKey({ charRequired: true, charDatePast: true })).toBe(
      'MSG-VAL-CHAR-REQUIRED',
    );
  });
});
