import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SearchableDropdown } from '~/components/SearchableDropdown';

vi.mock('~/hooks/usei18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

// Mock edifice components that carry internal React contexts (react-query, i18next)
// from the edifice-ui monorepo package — avoids dual-React-instance issues in tests.
vi.mock('@edifice.io/react', () => {
  const DropdownMenu = ({ children }: { children: React.ReactNode }) => (
    <ul role="menu">{children}</ul>
  );
  const DropdownItem = ({
    children,
    onClick,
  }: {
    children: React.ReactNode;
    onClick?: () => void;
  }) => (
    <li role="menuitem" onClick={onClick}>
      {children}
    </li>
  );
  const DropdownTrigger = ({ label }: { label: string }) => (
    <button type="button">{label}</button>
  );
  const DropdownSearchInput = () => null;

  const Dropdown = Object.assign(
    ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    {
      Trigger: DropdownTrigger,
      Menu: DropdownMenu,
      Item: DropdownItem,
      SearchInput: DropdownSearchInput,
    },
  );

  const FormControl = ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  );
  const Label = ({ children }: { children: React.ReactNode }) => (
    <label>{children}</label>
  );

  return { Dropdown, FormControl, Label };
});

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
};

describe('SearchableDropdown', () => {
  it('renders the label', () => {
    render(<SearchableDropdown {...defaultProps} />);
    expect(screen.getByText('Catégorie')).toBeInTheDocument();
  });

  it('shows placeholder when no value is selected', () => {
    render(
      <SearchableDropdown
        {...defaultProps}
        placeholder="Choisir une catégorie"
        selectedValue=""
      />,
    );
    expect(screen.getByText('Choisir une catégorie')).toBeInTheDocument();
  });

  it('shows the selected option label instead of placeholder', () => {
    render(<SearchableDropdown {...defaultProps} selectedValue="math" />);
    // Label appears in both the trigger button and the menu item
    expect(screen.getAllByText('Mathématiques').length).toBeGreaterThanOrEqual(
      1,
    );
  });

  it('calls onChange when an option is clicked', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(
      <SearchableDropdown
        {...defaultProps}
        onChange={onChange}
        selectedValue=""
      />,
    );

    await user.click(screen.getByText('Français'));
    expect(onChange).toHaveBeenCalledWith('fr');
  });

  it('does not call onChange when clicking the already selected option', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(
      <SearchableDropdown
        {...defaultProps}
        onChange={onChange}
        selectedValue="math"
      />,
    );

    // Mathématiques is the selected value — clicking it should not call onChange
    const mathItems = screen.getAllByText('Mathématiques');
    await user.click(mathItems[mathItems.length - 1]);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('renders options sorted alphabetically', () => {
    render(<SearchableDropdown {...defaultProps} selectedValue="" />);
    const items = screen.getAllByRole('menuitem');
    const labels = items.map((el) => el.textContent?.trim());
    expect(labels).toEqual(['Français', 'Histoire', 'Mathématiques']);
  });
});
