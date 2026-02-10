(function () {
  function normalize(value) {
    return (value || '').toString().trim().toLowerCase();
  }

  function getHeaderCells(table) {
    const theadHeader = table.querySelector('thead tr');
    if (theadHeader && theadHeader.querySelectorAll('th').length) {
      return Array.from(theadHeader.querySelectorAll('th'));
    }
    const firstRow = table.querySelector('tr');
    if (!firstRow) return [];
    return Array.from(firstRow.children).filter((cell) => cell.tagName === 'TH');
  }

  function getBodyRows(table) {
    const tbodyRows = table.querySelectorAll('tbody tr');
    if (tbodyRows.length) return Array.from(tbodyRows);
    const rows = Array.from(table.querySelectorAll('tr'));
    return rows.slice(1);
  }

  function ensureThead(table, headerRow) {
    if (table.querySelector('thead')) return;
    const thead = document.createElement('thead');
    thead.appendChild(headerRow);
    table.insertBefore(thead, table.firstChild);
  }

  function addFilters(table) {
    if (table.dataset.columnFilters === 'ready') return;

    const headerCells = getHeaderCells(table);
    if (!headerCells.length) return;

    const headerRow = headerCells[0].parentElement;
    ensureThead(table, headerRow);

    const filterRow = document.createElement('tr');
    filterRow.className = 'column-filter-row';

    headerCells.forEach((headerCell, index) => {
      const filterCell = document.createElement('th');
      const input = document.createElement('input');
      input.type = 'text';
      input.className = 'column-filter-input';
      input.placeholder = 'Фильтр';
      input.setAttribute('aria-label', 'Фильтр по колонке ' + headerCell.textContent.trim());
      input.dataset.columnIndex = String(index);
      filterCell.appendChild(input);
      filterRow.appendChild(filterCell);
    });

    const thead = table.querySelector('thead');
    thead.appendChild(filterRow);

    const inputs = Array.from(filterRow.querySelectorAll('input'));
    const rows = getBodyRows(table);

    function applyFilters() {
      const terms = inputs.map((input) => normalize(input.value));
      rows.forEach((row) => {
        const cells = Array.from(row.children);
        const visible = terms.every((term, idx) => {
          if (!term) return true;
          return normalize(cells[idx] ? cells[idx].textContent : '').includes(term);
        });
        row.style.display = visible ? '' : 'none';
      });
    }

    inputs.forEach((input) => input.addEventListener('input', applyFilters));
    table.dataset.columnFilters = 'ready';
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('table').forEach(addFilters);
  });
})();
