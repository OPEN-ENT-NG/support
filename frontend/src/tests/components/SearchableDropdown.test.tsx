import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { SearchableDropdown } from '~/components/SearchableDropdown';

vi.mock('~/hooks/usei18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

// Minimal wrapper: Dropdown internals use no react-query, but FormControl might
// pull in hooks that do. A fresh QueryClient per test prevents cross-test leakage.
function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

const options = [
  { label: 'Mathématiques', value: 'math' },
  { label: 'Français', value: 'fr' },
  { label: 'Histoire', value: 'hist' },
];

const defaultProps = {
  id: 'test',
  label: 'Catégorie',
  placeholder: 'Choisir',
  searchPlaceholder: 'Rechercher',
  options,
  selectedValue: '',
  onChange: vi.fn(),
  disabled: false,
};

function renderDropdown(props: Partial<typeof defaultProps> = {}) {
  return render(<SearchableDropdown {...defaultProps} {...props} />, {
    wrapper: makeWrapper(),
  });
}

async function openDropdown() {
  const user = userEvent.setup();
  await user.click(screen.getByRole('button'));
  return user;
}

describe('SearchableDropdown', () => {
  it('renders the label', () => {
    renderDropdown();
    expect(screen.getByText('Catégorie')).toBeInTheDocument();
  });

  it('shows placeholder in the trigger when no value is selected', () => {
    renderDropdown({ placeholder: 'Choisir une catégorie', selectedValue: '' });
    expect(
      screen.getByRole('button', { name: /Choisir une catégorie/i }),
    ).toBeInTheDocument();
  });

  it('shows the selected option label in the trigger instead of placeholder', () => {
    renderDropdown({ selectedValue: 'math' });
    expect(
      screen.getByRole('button', { name: /Mathématiques/i }),
    ).toBeInTheDocument();
  });

  it('opens the menu and shows all options when trigger is clicked', async () => {
    renderDropdown();
    await openDropdown();
    const items = screen.getAllByRole('menuitem');
    expect(items).toHaveLength(options.length);
  });

  it('renders options sorted alphabetically', async () => {
    renderDropdown({ selectedValue: '' });
    await openDropdown();
    const labels = screen
      .getAllByRole('menuitem')
      .map((el) => el.textContent?.trim());
    expect(labels).toEqual(['Français', 'Histoire', 'Mathématiques']);
  });

  it('calls onChange with the selected value when an option is clicked', async () => {
    const onChange = vi.fn();
    renderDropdown({ onChange, selectedValue: '' });
    const user = await openDropdown();
    await user.click(screen.getByRole('menuitem', { name: 'Français' }));
    expect(onChange).toHaveBeenCalledWith('fr');
  });

  it('does not call onChange when the already-selected option is clicked', async () => {
    const onChange = vi.fn();
    renderDropdown({ onChange, selectedValue: 'math' });
    const user = await openDropdown();
    await user.click(screen.getByRole('menuitem', { name: 'Mathématiques' }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('hides the trigger caret label when disabled', () => {
    renderDropdown({ disabled: true });
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
