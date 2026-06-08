// ── Global State (outside DOMContentLoaded so dynamic onclick can read them) ──
let selectedFiles = [];
let currentZoom = 1, currentRotate = 0;
let currentTblCod = '';
let selectedTabCod = null, selectedTabCodNam = '';
let selectedDtlCod = null;
let selectedDtsSubCod = null;
let editingTabCod = null;
let configData = [];

document.addEventListener('DOMContentLoaded', function() {


// ── Tab ──────────────────────────────────────────────────────
function switchTab(tab, btn) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content-area').forEach(c => c.classList.remove('active'));
    document.getElementById('tab-' + tab).classList.add('active');
    btn.classList.add('active');
}

// ── User Menu ─────────────────────────────────────────────────
function toggleUserMenu(e) {
    e.stopPropagation();
    document.getElementById('user-dropdown').classList.toggle('open');
}
document.addEventListener('click', () => document.getElementById('user-dropdown').classList.remove('open'));

// ── Modal ─────────────────────────────────────────────────────
function openModal(id) { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }
document.querySelectorAll('.modal-overlay').forEach(el => {
    el.addEventListener('click', function(e) { if (e.target === this) closeModal(this.id); });
});

// ── Program Configuration ─────────────────────────────────────
function openProgramConfig() {
    document.getElementById('user-dropdown').classList.remove('open');
    openModal('modal-config');
    loadConfig();
}

async function loadConfig() {
    const search = document.getElementById('config-search').value;
    document.getElementById('config-loading').style.display = 'block';
    document.getElementById('config-table').style.display = 'none';
    try {
        const res = await fetch('/api/config?search=' + encodeURIComponent(search));
        configData = await res.json();
        renderConfig(configData);
    } catch(e) { showToast('โหลดข้อมูลล้มเหลว: ' + e.message, 'error'); }
    document.getElementById('config-loading').style.display = 'none';
    document.getElementById('config-table').style.display = '';
}

function renderConfig(data) {
    document.getElementById('config-count').textContent = data.length + ' รายการ';
    const tbody = document.getElementById('config-tbody');
    tbody.innerHTML = data.map((row) => {
        const name = row.DtlCodNam || '';
        const val  = row.DtlCodVal || '';
        const cod  = row.DtlCod || '';
        const pipeIdx = name.indexOf('|');
        let label = name, optStr = '';
        if (pipeIdx >= 0) { label = name.substring(0, pipeIdx); optStr = name.substring(pipeIdx + 1); }
        label = label.trim();
        let inputHtml;
        if (optStr) {
            const opts = optStr.split('|').map(o => o.trim()).filter(Boolean);
            inputHtml = '<select class="config-val-select" data-cod="' + escHtml(cod) + '">' +
                opts.map(o => '<option value="' + escHtml(o) + '"' + (o === val ? ' selected' : '') + '>' + escHtml(o) + '</option>').join('') +
                '</select>';
        } else {
            inputHtml = '<input class="config-val-input" type="text" data-cod="' + escHtml(cod) + '" value="' + escHtml(val) + '">';
        }
        return '<tr><td style="color:var(--text-muted);font-size:0.83rem;">' + escHtml(label) + '</td><td>' + inputHtml + '</td></tr>';
    }).join('');
}

async function saveConfig() {
    const inputs = document.querySelectorAll('#config-tbody [data-cod]');
    const items = Array.from(inputs).map(el => ({ dtlCod: el.dataset.cod, dtlCodVal: el.value }));
    const userId = document.getElementById('scan-userid').value || 'DEMO';
    try {
        const res = await fetch('/api/config/save?userId=' + encodeURIComponent(userId), {
            method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(items)
        });
        const data = await res.json();
        if (data.success) { showToast('บันทึกสำเร็จ', 'success'); closeModal('modal-config'); }
        else showToast(data.error || 'บันทึกล้มเหลว', 'error');
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
}

// ── Detail Master ─────────────────────────────────────────────
function openDetailMaster() {
    document.getElementById('user-dropdown').classList.remove('open');
    openModal('modal-master');
    loadTabTyp();
}

async function loadTabTyp() {
    try {
        const res = await fetch('/api/master/tabtyp');
        const data = await res.json();
        const sel = document.getElementById('master-tabtyp');
        sel.innerHTML = '<option value="">-- เลือกประเภท --</option>' +
            data.map(r => '<option value="' + escHtml(r.DtlCod||'') + '">' +
                escHtml(r.DtlCodNam||r.DtlCod||'') + '</option>').join('');
    } catch(e) { showToast('โหลด TabTyp ล้มเหลว', 'error'); }
}

async function loadTabMst() {
    const typ = document.getElementById('master-tabtyp').value;
    // reset all state
    selectedTabCod = null; selectedTabCodNam = '';
    currentTblCod = ''; selectedDtlCod = null; selectedDtsSubCod = null;
    document.getElementById('master-dtlmst-body').innerHTML =
        '<tr><td colspan="5" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;">เลือกรายการด้านซ้าย</td></tr>';
    document.getElementById('master-dtsmst-body').innerHTML =
        '<tr><td colspan="6" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;"></td></tr>';
    if (!typ) {
        document.getElementById('master-tabmst-body').innerHTML =
            '<tr><td colspan="2" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;">เลือกประเภทด้านบน</td></tr>';
        return;
    }
    document.getElementById('master-tabmst-body').innerHTML =
        '<tr><td colspan="2" class="text-center py-2"><span class="loading-spinner"></span></td></tr>';
    try {
        const res = await fetch('/api/master/tabmst?tabCodTyp=' + encodeURIComponent(typ));
        const data = await res.json();
        document.getElementById('master-tabmst-body').innerHTML = data.length
            ? data.map(r => '<tr class="clickable" onclick="selectTabRow(\'' + (r.TabCod||'').trim().replace(/'/g,"\\'") + '\',\'' + (r.TabCodNam||'').trim().replace(/'/g,"\\'") + '\', this)">' +
                '<td style="font-family:monospace;font-size:0.8rem;">' + escHtml(r.TabCod||'') + '</td>' +
                '<td>' + escHtml(r.TabCodNam||'') + '</td></tr>').join('')
            : '<tr><td colspan="2" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;">ไม่พบข้อมูล</td></tr>';
    } catch(e) { showToast('โหลด TabMst ล้มเหลว', 'error'); }
}

function selectTabRow(tabCod, tabCodNam, row) {
    document.querySelectorAll('#master-tabmst-body tr').forEach(r => r.classList.remove('selected'));
    row.classList.add('selected');
    selectedTabCod = tabCod.trim();
    selectedTabCodNam = tabCodNam.trim();
    currentTblCod = tabCod.trim();
    document.getElementById('f-tab-typ').value = document.getElementById('master-tabtyp').value;
    selectedDtlCod = null; selectedDtsSubCod = null;
    document.getElementById('master-dtsmst-body').innerHTML =
        '<tr><td colspan="6" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;"></td></tr>';
    loadDtlMstData(tabCod);
}

async function loadDtlMstData(tabCod) {
    document.getElementById('master-dtlmst-body').innerHTML =
        '<tr><td colspan="5" class="text-center py-2"><span class="loading-spinner"></span></td></tr>';
    try {
        const res = await fetch('/api/master/dtlmst?dtlTblCod=' + encodeURIComponent(tabCod.trim()));
        const data = await res.json();
        selectedDtlCod = null;
        document.getElementById('master-dtlmst-body').innerHTML = data.length
            ? data.map((r,i) => '<tr class="clickable" draggable="true" data-draggable="1" data-cod="' + escHtml(r.DtlCod||'') + '" onclick="selectDtlRow(\'' + (r.DtlCod||'').trim().replace(/'/g,"\\'") + '\', this)">' +
                '<td><span class="drag-handle">⠿</span></td>' +
                '<td style="font-family:monospace;font-size:0.79rem;">' + escHtml(r.DtlCod||'') + '</td>' +
                '<td>' + escHtml(r.DtlCodNam||'') + '</td>' +
                '<td style="color:var(--text-muted);font-size:0.79rem;">' + escHtml((r.DtlCodVal||'').substring(0,30)) + '</td>' +
                '</tr>').join('')
            : '<tr><td colspan="4" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;">ไม่พบข้อมูล</td></tr>';
    } catch(e) { showToast('โหลด DtlMst ล้มเหลว', 'error'); }
}

function selectDtlRow(dtlCod, row) {
    document.querySelectorAll('#master-dtlmst-body tr').forEach(r => r.classList.remove('selected'));
    row.classList.add('selected');
    selectedDtlCod = dtlCod;
    loadDtsMstData(dtlCod);
}

async function loadDtsMstData(dtlCod) {
    document.getElementById('master-dtsmst-body').innerHTML =
        '<tr><td colspan="6" class="text-center py-2"><span class="loading-spinner"></span></td></tr>';
    try {
        const res = await fetch('/api/master/dtsmst?dtsTblCod=' + encodeURIComponent(currentTblCod) + '&dtsCod=' + encodeURIComponent(dtlCod));
        const data = await res.json();
        selectedDtsSubCod = null;
        document.getElementById('master-dtsmst-body').innerHTML = data.length
            ? data.map((r,i) => '<tr class="clickable" draggable="true" data-draggable="1" data-cod="' + escHtml(r.DtsSubCod||'') + '" onclick="selectDtsRow(\'' + (r.DtsSubCod||'').trim().replace(/'/g,"\\'") + '\', this)">' +
                '<td><span class="drag-handle">⠿</span></td>' +
                '<td style="font-family:monospace;font-size:0.79rem;">' + escHtml(r.DtsSubCod||'') + '</td>' +
                '<td>' + escHtml(r.DtsCodNam||'') + '</td>' +
                '<td style="color:var(--text-muted);font-size:0.79rem;">' + escHtml((r.DtsCodVal||'').substring(0,30)) + '</td>' +
                '</tr>').join('')
            : '<tr><td colspan="5" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;">ไม่มีข้อมูล Sub</td></tr>';
    } catch(e) { showToast('โหลด DtsMst ล้มเหลว', 'error'); }
}

function selectDtsRow(subCod, row) {
    document.querySelectorAll('#master-dtsmst-body tr').forEach(r => r.classList.remove('selected'));
    row.classList.add('selected');
    selectedDtsSubCod = subCod;
}

// ── CRUD Popup ───────────────────────────────────────────────
let crudMode = null; // 'tabmst-add','tabmst-edit','dtlmst-add','dtlmst-edit','dtsmst-add','dtsmst-edit'

function openCrudForm(mode, title, icon, bodyHtml, saveFn) {
    // close any open crud first
    closeCrudForm();
    crudMode = mode;
    document.getElementById('crud-title').textContent = title;
    document.getElementById('crud-icon').className = 'bi ' + icon;
    document.getElementById('crud-body').innerHTML = bodyHtml;
    document.getElementById('crud-save-btn').onclick = saveFn;
    openModal('modal-crud');
}

function closeCrudForm() {
    closeModal('modal-crud');
    crudMode = null;
}


// ── Confirm Dialog ────────────────────────────────────────────
function showConfirm(message, onConfirm) {
    const overlay = document.createElement('div');
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(15,23,42,0.5);backdrop-filter:blur(4px);z-index:9000;display:flex;align-items:center;justify-content:center;';
    overlay.innerHTML = `
        <div style="background:white;border-radius:12px;box-shadow:0 20px 60px rgba(0,0,0,0.2);width:360px;overflow:hidden;animation:modalIn 0.2s ease;">
            <div style="padding:1.25rem 1.5rem;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:0.6rem;">
                <i class="bi bi-exclamation-triangle-fill" style="color:#f59e0b;font-size:1.1rem;"></i>
                <span style="font-weight:600;font-size:0.95rem;color:var(--text);">ยืนยันการลบ</span>
            </div>
            <div style="padding:1.25rem 1.5rem;font-size:0.88rem;color:var(--text-muted);">${message}</div>
            <div style="padding:0.75rem 1.5rem;border-top:1px solid var(--border);display:flex;gap:0.5rem;justify-content:flex-end;">
                <button id="confirm-cancel" style="background:white;border:1px solid var(--border);color:var(--text);padding:0.45rem 1rem;border-radius:6px;font-size:0.86rem;cursor:pointer;">ยกเลิก</button>
                <button id="confirm-ok" style="background:var(--danger);border:none;color:white;padding:0.45rem 1rem;border-radius:6px;font-size:0.86rem;cursor:pointer;font-weight:500;"><i class="bi bi-trash"></i> ลบ</button>
            </div>
        </div>`;
    document.body.appendChild(overlay);
    document.getElementById('confirm-ok').onclick = () => { overlay.remove(); onConfirm(); };
    document.getElementById('confirm-cancel').onclick = () => overlay.remove();
    overlay.addEventListener('click', e => { if (e.target === overlay) overlay.remove(); });
}

// ── TabMst CRUD ───────────────────────────────────────────────
function showTabMstForm() {
    openCrudForm('tabmst-add', 'เพิ่ม Table', 'bi-plus-lg',
        `<div class="mb-3"><label class="form-label">Table Code *</label>
         <input id="f-tab-cod" class="form-control" maxlength="8" style="text-transform:uppercase" placeholder="max 8 ตัวอักษร"></div>
         <div class="mb-3"><label class="form-label">Table Code Name</label>
         <input id="f-tab-nam" class="form-control"></div>
         <input type="hidden" id="f-tab-typ" value="${document.getElementById('master-tabtyp').value}">`,
        saveTabMst);
    setTimeout(() => document.getElementById('f-tab-cod') && document.getElementById('f-tab-cod').focus(), 100);
}

function editTabMstRow() {
    if (!selectedTabCod) { showToast('กรุณาเลือกรายการก่อน', 'info'); return; }
    openCrudForm('tabmst-edit', 'แก้ไข Table', 'bi-pencil',
        `<div class="mb-3"><label class="form-label">Table Code</label>
         <input class="form-control" value="${escHtml(selectedTabCod)}" readonly style="background:#f8fafc;"></div>
         <div class="mb-3"><label class="form-label">Table Code Name</label>
         <input id="f-tab-nam" class="form-control" value="${escHtml(selectedTabCodNam)}"></div>
         <input type="hidden" id="f-tab-cod" value="${escHtml(selectedTabCod)}">
         <input type="hidden" id="f-tab-typ" value="${document.getElementById('master-tabtyp').value}">`,
        saveTabMst);
    setTimeout(() => document.getElementById('f-tab-nam') && document.getElementById('f-tab-nam').focus(), 100);
}

async function saveTabMst() {
    const tabCod = (document.getElementById('f-tab-cod').value || '').trim().toUpperCase();
    const tabCodNam = (document.getElementById('f-tab-nam').value || '').trim();
    const tabCodTyp = (document.getElementById('f-tab-typ').value || '').trim().toUpperCase();
    if (!tabCod) { showToast('กรุณากรอก Table Code', 'error'); return; }
    const url = (crudMode === 'tabmst-edit') ? '/api/master/tabmst/update' : '/api/master/tabmst/insert';
    try {
        const res = await fetch(url, { method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({ tabCod, tabCodNam, tabCodTyp }) });
        const data = await res.json();
        if (data.success) { showToast('บันทึกสำเร็จ', 'success'); closeCrudForm(); loadTabMst(); }
        else showToast(data.error || 'ล้มเหลว', 'error');
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
}

async function deleteTabMstRow() {
    if (!selectedTabCod) { showToast('กรุณาเลือกรายการก่อน', 'info'); return; }
    showConfirm('ลบ Table Code: <strong>' + selectedTabCod + '</strong> ?', async () => {
    try {
        const res = await fetch('/api/master/tabmst/' + encodeURIComponent(selectedTabCod), { method:'DELETE' });
        const data = await res.json();
        if (data.success) { showToast('ลบสำเร็จ', 'success'); selectedTabCod = null; selectedTabCodNam = ''; currentTblCod = ''; loadTabMst(); }
        else showToast(data.error || 'ล้มเหลว', 'error');
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
    });
}

// ── DtlMst CRUD ───────────────────────────────────────────────
let editingDtlCod = null;

function showDtlMstForm() {
    if (!currentTblCod) { showToast('กรุณาเลือก Table ด้านซ้ายก่อน', 'info'); return; }
    editingDtlCod = null;
    openCrudForm('dtlmst-add', 'เพิ่ม Detail', 'bi-plus-lg',
        `<div class="mb-3"><label class="form-label">DtlCod *</label>
         <input id="f-dtl-cod" class="form-control" maxlength="10" style="text-transform:uppercase"></div>
         <div class="mb-3"><label class="form-label">DtlCodNam</label>
         <input id="f-dtl-nam" class="form-control"></div>
         <div class="mb-3"><label class="form-label">DtlCodVal</label>
         <input id="f-dtl-val" class="form-control"></div>`,
        saveDtlMst);
    setTimeout(() => document.getElementById('f-dtl-cod') && document.getElementById('f-dtl-cod').focus(), 100);
}

function editDtlMstRow() {
    if (!selectedDtlCod) { showToast('กรุณาเลือกรายการก่อน', 'info'); return; }
    editingDtlCod = selectedDtlCod;
    // Get current values from selected row
    const row = document.querySelector('#master-dtlmst-body tr.selected');
    const cells = row ? row.querySelectorAll('td') : [];
    const curNam = cells[2] ? cells[2].textContent : '';
    const curVal = cells[3] ? cells[3].textContent : '';
    openCrudForm('dtlmst-edit', 'แก้ไข Detail', 'bi-pencil',
        `<div class="mb-3"><label class="form-label">DtlCod</label>
         <input class="form-control" value="${escHtml(selectedDtlCod)}" readonly style="background:#f8fafc;"></div>
         <div class="mb-3"><label class="form-label">DtlCodNam</label>
         <input id="f-dtl-nam" class="form-control" value="${escHtml(curNam)}"></div>
         <div class="mb-3"><label class="form-label">DtlCodVal</label>
         <input id="f-dtl-val" class="form-control" value="${escHtml(curVal)}"></div>`,
        saveDtlMst);
    setTimeout(() => document.getElementById('f-dtl-nam') && document.getElementById('f-dtl-nam').focus(), 100);
}

async function saveDtlMst() {
    const dtlCodNam = (document.getElementById('f-dtl-nam').value || '').trim();
    const dtlCodVal = (document.getElementById('f-dtl-val').value || '').trim();
    const userId = (document.getElementById('scan-userid') || {value:'DEMO'}).value || 'DEMO';
    if (crudMode === 'dtlmst-edit') {
        try {
            const res = await fetch('/api/master/dtlmst/update', { method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ dtlTblCod: currentTblCod, dtlCod: editingDtlCod, dtlCodNam, dtlCodVal, userId }) });
            const data = await res.json();
            if (data.success) { showToast('แก้ไขสำเร็จ', 'success'); closeCrudForm(); loadDtlMstData(currentTblCod); }
            else showToast(data.error || 'ล้มเหลว', 'error');
        } catch(e) { showToast('Error: ' + e.message, 'error'); }
    } else {
        const dtlCod = (document.getElementById('f-dtl-cod').value || '').trim().toUpperCase();
        if (!dtlCod) { showToast('กรุณากรอก DtlCod', 'error'); return; }
        try {
            const seqRes = await fetch('/api/master/dtlmst/nextseq?dtlTblCod=' + encodeURIComponent(currentTblCod));
            const seqData = await seqRes.json();
            const res = await fetch('/api/master/dtlmst/insert', { method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ dtlTblCod: currentTblCod, dtlCod, dtlCodNam, dtlCodVal, dtlDspSeq: String(seqData.seq), userId }) });
            const data = await res.json();
            if (data.success) { showToast('เพิ่มสำเร็จ', 'success'); closeCrudForm(); loadDtlMstData(currentTblCod); }
            else showToast(data.error || 'ล้มเหลว', 'error');
        } catch(e) { showToast('Error: ' + e.message, 'error'); }
    }
}

async function deleteDtlMstRow() {
    if (!selectedDtlCod) { showToast('กรุณาเลือกรายการก่อน', 'info'); return; }
    showConfirm('ลบ DtlCod: <strong>' + selectedDtlCod + '</strong> ?', async () => {
    try {
        const res = await fetch('/api/master/dtlmst?dtlTblCod=' + encodeURIComponent(currentTblCod) + '&dtlCod=' + encodeURIComponent(selectedDtlCod), { method:'DELETE' });
        const data = await res.json();
        if (data.success) {
            showToast('ลบสำเร็จ', 'success'); selectedDtlCod = null;
            loadDtlMstData(currentTblCod);
            document.getElementById('master-dtsmst-body').innerHTML = '<tr><td colspan="5" class="text-center py-3" style="color:var(--text-muted);font-size:0.82rem;"></td></tr>';
        } else showToast(data.error || 'ล้มเหลว', 'error');
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
    });
}

// ── DtsMst CRUD ───────────────────────────────────────────────
let editingDtsSubCod = null;

function showDtsMstForm() {
    if (!currentTblCod || !selectedDtlCod) { showToast('กรุณาเลือก Detail ด้านขวาบนก่อน', 'info'); return; }
    editingDtsSubCod = null;
    openCrudForm('dtsmst-add', 'เพิ่ม Sub Detail', 'bi-plus-lg',
        `<div class="mb-3"><label class="form-label">SubCod *</label>
         <input id="f-dts-sub" class="form-control" maxlength="10" style="text-transform:uppercase"></div>
         <div class="mb-3"><label class="form-label">DtsCodNam</label>
         <input id="f-dts-nam" class="form-control"></div>
         <div class="mb-3"><label class="form-label">DtsCodVal</label>
         <input id="f-dts-val" class="form-control"></div>`,
        saveDtsMst);
    setTimeout(() => document.getElementById('f-dts-sub') && document.getElementById('f-dts-sub').focus(), 100);
}

function editDtsMstRow() {
    if (!selectedDtsSubCod) { showToast('กรุณาเลือกรายการก่อน', 'info'); return; }
    editingDtsSubCod = selectedDtsSubCod;
    const row = document.querySelector('#master-dtsmst-body tr.selected');
    const cells = row ? row.querySelectorAll('td') : [];
    const curNam = cells[3] ? cells[3].textContent : '';
    const curVal = cells[4] ? cells[4].textContent : '';
    openCrudForm('dtsmst-edit', 'แก้ไข Sub Detail', 'bi-pencil',
        `<div class="mb-3"><label class="form-label">SubCod</label>
         <input class="form-control" value="${escHtml(selectedDtsSubCod)}" readonly style="background:#f8fafc;"></div>
         <div class="mb-3"><label class="form-label">DtsCodNam</label>
         <input id="f-dts-nam" class="form-control" value="${escHtml(curNam)}"></div>
         <div class="mb-3"><label class="form-label">DtsCodVal</label>
         <input id="f-dts-val" class="form-control" value="${escHtml(curVal)}"></div>`,
        saveDtsMst);
    setTimeout(() => document.getElementById('f-dts-nam') && document.getElementById('f-dts-nam').focus(), 100);
}

async function saveDtsMst() {
    const dtsCodNam = (document.getElementById('f-dts-nam').value || '').trim();
    const dtsCodVal = (document.getElementById('f-dts-val').value || '').trim();
    const userId = (document.getElementById('scan-userid') || {value:'DEMO'}).value || 'DEMO';
    if (crudMode === 'dtsmst-edit') {
        try {
            const res = await fetch('/api/master/dtsmst/update', { method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ dtsTblCod: currentTblCod, dtsCod: selectedDtlCod, dtsSubCod: editingDtsSubCod, dtsCodNam, dtsCodVal, userId }) });
            const data = await res.json();
            if (data.success) { showToast('แก้ไขสำเร็จ', 'success'); closeCrudForm(); loadDtsMstData(selectedDtlCod); }
            else showToast(data.error || 'ล้มเหลว', 'error');
        } catch(e) { showToast('Error: ' + e.message, 'error'); }
    } else {
        const dtsSubCod = (document.getElementById('f-dts-sub').value || '').trim().toUpperCase();
        if (!dtsSubCod) { showToast('กรุณากรอก SubCod', 'error'); return; }
        try {
            const seqRes = await fetch('/api/master/dtsmst/nextseq?dtsTblCod=' + encodeURIComponent(currentTblCod) + '&dtsCod=' + encodeURIComponent(selectedDtlCod));
            const seqData = await seqRes.json();
            const res = await fetch('/api/master/dtsmst/insert', { method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ dtsTblCod: currentTblCod, dtsCod: selectedDtlCod, dtsSubCod, dtsCodNam, dtsCodVal, dtsDspSeq: String(seqData.seq), userId }) });
            const data = await res.json();
            if (data.success) { showToast('เพิ่มสำเร็จ', 'success'); closeCrudForm(); loadDtsMstData(selectedDtlCod); }
            else showToast(data.error || 'ล้มเหลว', 'error');
        } catch(e) { showToast('Error: ' + e.message, 'error'); }
    }
}

async function deleteDtsMstRow() {
    if (!selectedDtsSubCod) { showToast('กรุณาเลือกรายการก่อน', 'info'); return; }
    showConfirm('ลบ SubCod: <strong>' + selectedDtsSubCod + '</strong> ?', async () => {
    try {
        const res = await fetch('/api/master/dtsmst?dtsTblCod=' + encodeURIComponent(currentTblCod) + '&dtsCod=' + encodeURIComponent(selectedDtlCod) + '&dtsSubCod=' + encodeURIComponent(selectedDtsSubCod), { method:'DELETE' });
        const data = await res.json();
        if (data.success) { showToast('ลบสำเร็จ', 'success'); selectedDtsSubCod = null; loadDtsMstData(selectedDtlCod); }
        else showToast(data.error || 'ล้มเหลว', 'error');
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
    });
}


// ── Drag to reorder ───────────────────────────────────────────
function initDragReorder(tbodyId, onDrop) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) return;
    let dragSrc = null;

    function onDragStart(e) { dragSrc = this; this.classList.add('dragging'); e.dataTransfer.effectAllowed = 'move'; }
    function onDragOver(e) { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; document.querySelectorAll('#' + tbodyId + ' tr').forEach(r => r.classList.remove('drag-over')); this.classList.add('drag-over'); }
    function onDragLeave() { this.classList.remove('drag-over'); }
    function onDragEnd() { document.querySelectorAll('#' + tbodyId + ' tr').forEach(r => { r.classList.remove('dragging'); r.classList.remove('drag-over'); }); }
    function onDropFn(e) {
        e.stopPropagation();
        if (dragSrc !== this) {
            const allRows = Array.from(tbody.querySelectorAll('tr'));
            const fromIdx = allRows.indexOf(dragSrc);
            const toIdx = allRows.indexOf(this);
            if (fromIdx < toIdx) this.after(dragSrc); else this.before(dragSrc);
            onDrop();
        }
    }

    new MutationObserver(() => {
        tbody.querySelectorAll('tr[data-draggable]').forEach(row => {
            row.setAttribute('draggable', 'true');
            row.removeEventListener('dragstart', onDragStart);
            row.removeEventListener('dragover',  onDragOver);
            row.removeEventListener('dragleave', onDragLeave);
            row.removeEventListener('dragend',   onDragEnd);
            row.removeEventListener('drop',      onDropFn);
            row.addEventListener('dragstart', onDragStart);
            row.addEventListener('dragover',  onDragOver);
            row.addEventListener('dragleave', onDragLeave);
            row.addEventListener('dragend',   onDragEnd);
            row.addEventListener('drop',      onDropFn);
        });
    }).observe(tbody, { childList: true });
}

async function saveDtlOrder() {
    const rows = Array.from(document.querySelectorAll('#master-dtlmst-body tr[data-draggable]'));
    const items = rows.map(r => ({ dtlCod: r.dataset.cod }));
    try {
        await fetch('/api/master/dtlmst/reorder?dtlTblCod=' + encodeURIComponent(currentTblCod), {
            method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(items) });
        rows.forEach((r, i) => { const s = r.querySelector('.seq-cell'); if (s) s.textContent = i+1; });
    } catch(e) { showToast('บันทึกลำดับล้มเหลว', 'error'); }
}

async function saveDtsOrder() {
    const rows = Array.from(document.querySelectorAll('#master-dtsmst-body tr[data-draggable]'));
    const items = rows.map(r => ({ dtsSubCod: r.dataset.cod }));
    try {
        await fetch('/api/master/dtsmst/reorder?dtsTblCod=' + encodeURIComponent(currentTblCod) + '&dtsCod=' + encodeURIComponent(selectedDtlCod), {
            method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(items) });
        rows.forEach((r, i) => { const s = r.querySelector('.seq-cell'); if (s) s.textContent = i+1; });
    } catch(e) { showToast('บันทึกลำดับล้มเหลว', 'error'); }
}

initDragReorder('master-dtlmst-body', saveDtlOrder);
initDragReorder('master-dtsmst-body', saveDtsOrder);

// Init HN config on load
initHnConfig();

// ── Scan ──────────────────────────────────────────────────────
function handleFileSelect(e) { addFiles(Array.from(e.target.files)); e.target.value = ''; }
function handleDragOver(e) { e.preventDefault(); document.getElementById('drop-zone').classList.add('dragover'); }
function handleDragLeave(e) { document.getElementById('drop-zone').classList.remove('dragover'); }
function handleDrop(e) { e.preventDefault(); document.getElementById('drop-zone').classList.remove('dragover'); addFiles(Array.from(e.dataTransfer.files)); }

function addFiles(files) {
    const allowed = ['image/jpeg','image/png','image/tiff','application/pdf'];
    files.forEach(f => {
        if (!allowed.includes(f.type) && !f.name.match(/\.(tif|tiff)$/i)) { showToast('ไฟล์ ' + f.name + ' ไม่รองรับ', 'error'); return; }
        selectedFiles.push(f);
    });
    renderPreviews(); updateUploadBtn();
}

function renderPreviews() {
    const grid = document.getElementById('preview-grid');
    grid.innerHTML = '';
    selectedFiles.forEach((f, i) => {
        const item = document.createElement('div');
        item.className = 'preview-item';
        let imgHtml = f.type.startsWith('image/')
            ? '<img src="' + URL.createObjectURL(f) + '" alt="' + escHtml(f.name) + '">'
            : '<div style="height:110px;display:flex;align-items:center;justify-content:center;background:#fef2f2;"><i class="bi bi-file-pdf" style="font-size:1.8rem;color:#dc2626;"></i></div>';
        item.innerHTML = imgHtml +
            '<div class="preview-info" title="' + escHtml(f.name) + '">' + (i+1) + '. ' + escHtml(f.name) + '</div>' +
            '<button class="remove-btn" onclick="removeFile(' + i + ')"><i class="bi bi-x"></i></button>';
        grid.appendChild(item);
    });
}
function removeFile(i) { selectedFiles.splice(i,1); renderPreviews(); updateUploadBtn(); }
function updateUploadBtn() { document.getElementById('upload-count').textContent = selectedFiles.length; document.getElementById('btn-upload').disabled = selectedFiles.length === 0; }

async function submitScan() {
    const hn = getHnValue('scan').toUpperCase();
    const formCode = document.getElementById('scan-formcode').value;
    const userId = document.getElementById('scan-userid').value.trim() || 'DEMO';
    if (!hn) { showToast('กรุณากรอก HN', 'error'); return; }
    if (!formCode) { showToast('กรุณาเลือกฟอร์ม', 'error'); return; }
    if (selectedFiles.length === 0) { showToast('กรุณาเลือกไฟล์', 'error'); return; }
    const btn = document.getElementById('btn-upload');
    const progressDiv = document.getElementById('upload-progress');
    btn.disabled = true; progressDiv.style.display = 'block';
    let success = 0, fail = 0;
    for (let i = 0; i < selectedFiles.length; i++) {
        document.getElementById('progress-text').textContent = i + '/' + selectedFiles.length;
        document.getElementById('progress-bar').style.width = (i / selectedFiles.length * 100) + '%';
        const fd = new FormData();
        fd.append('hn', hn); fd.append('formCode', formCode); fd.append('userId', userId); fd.append('file', selectedFiles[i]);
        try {
            const res = await fetch('/api/scan/upload', { method: 'POST', body: fd });
            const data = await res.json();
            if (res.ok && data.success) success++; else { fail++; showToast('ไฟล์ ' + selectedFiles[i].name + ': ' + (data.error||'error'), 'error'); }
        } catch(e) { fail++; showToast('ไฟล์ ' + selectedFiles[i].name + ': ' + e.message, 'error'); }
    }
    document.getElementById('progress-text').textContent = selectedFiles.length + '/' + selectedFiles.length;
    document.getElementById('progress-bar').style.width = '100%';
    if (success > 0) { showToast('บันทึกสำเร็จ ' + success + ' ไฟล์', 'success'); selectedFiles = []; renderPreviews(); updateUploadBtn(); }
    if (fail > 0) showToast('ล้มเหลว ' + fail + ' ไฟล์', 'error');
    setTimeout(() => { progressDiv.style.display = 'none'; btn.disabled = false; }, 1500);
}

// ── Viewer ────────────────────────────────────────────────────
async function searchTreatments() {
    const hn = getHnValue('view').toUpperCase();
    if (!hn) { showToast('กรุณากรอก HN', 'error'); return; }
    const status = document.getElementById('viewer-status');
    status.innerHTML = '<span class="loading-spinner"></span>';
    document.getElementById('chartpage-list').innerHTML = '<div class="empty-state"><i class="bi bi-hand-index"></i><p>เลือกรายการการรักษาด้านซ้าย</p></div>';
    document.getElementById('page-count').textContent = '';
    try {
        const res = await fetch('/api/treatments?hn=' + encodeURIComponent(hn));
        const data = await res.json();
        status.textContent = '';
        document.getElementById('treat-count').textContent = data.length + ' รายการ';
        if (data.length === 0) {
            document.getElementById('treatment-list').innerHTML = '<div class="empty-state"><i class="bi bi-inbox"></i><p>ไม่พบข้อมูล HN นี้</p></div>';
            return;
        }
        document.getElementById('treatment-list').innerHTML =
            '<table class="result-table"><thead><tr><th>วันที่</th><th>TreatNo</th><th>Class</th></tr></thead><tbody>' +
            data.map(t => '<tr class="clickable" onclick="loadChartPages(' + t.treatNo + ', this)"><td>' + formatDate(t.inDate) + '</td><td style="font-family:monospace">' + t.treatNo + '</td><td>' + (t.classType||'-') + '</td></tr>').join('') +
            '</tbody></table>';
    } catch(e) { status.textContent = ''; showToast('เกิดข้อผิดพลาด: ' + e.message, 'error'); }
}

async function loadChartPages(treatNo, row) {
    document.querySelectorAll('#treatment-list tr').forEach(r => r.classList.remove('selected'));
    row.classList.add('selected');
    const chartList = document.getElementById('chartpage-list');
    chartList.innerHTML = '<div class="empty-state"><span class="loading-spinner"></span></div>';
    try {
        const res = await fetch('/api/chartpages/' + treatNo);
        const data = await res.json();
        document.getElementById('page-count').textContent = data.length + ' ภาพ';
        if (data.length === 0) { chartList.innerHTML = '<div class="empty-state"><i class="bi bi-images"></i><p>ไม่พบภาพ</p></div>'; return; }
        chartList.innerHTML = '<table class="result-table"><thead><tr><th>ฟอร์ม</th><th>หน้า</th><th>วันที่</th><th>PAGENO</th><th>ดูภาพ</th></tr></thead><tbody>' +
            data.map(p => '<tr><td><span class="badge-form">' + escHtml(p.formCode||'-') + '</span><div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px">' + escHtml(p.formName||'') + '</div></td>' +
                '<td><span class="badge-page">หน้า ' + p.page + '</span></td>' +
                '<td>' + formatDate(p.cDate) + '</td>' +
                '<td style="font-family:monospace;font-size:0.78rem">' + p.pageNo + '</td>' +
                '<td><button class="btn-outline-custom" style="padding:0.25rem 0.55rem;font-size:0.78rem" onclick="openViewer(' + JSON.stringify(p.filePath) + ', ' + JSON.stringify('หน้า ' + p.page + ' — ' + (p.formCode||'')) + ')"><i class="bi bi-eye"></i> ดู</button></td></tr>').join('') +
            '</tbody></table>';
    } catch(e) { showToast('เกิดข้อผิดพลาด: ' + e.message, 'error'); }
}

function clearViewer() {
    document.getElementById('view-hn').value = '';
    document.getElementById('treatment-list').innerHTML = '<div class="empty-state"><i class="bi bi-search"></i><p>กรอก HN เพื่อค้นหา</p></div>';
    document.getElementById('chartpage-list').innerHTML = '<div class="empty-state"><i class="bi bi-hand-index"></i><p>เลือกรายการการรักษาด้านซ้าย</p></div>';
    document.getElementById('treat-count').textContent = '';
    document.getElementById('page-count').textContent = '';
    document.getElementById('viewer-status').textContent = '';
}

// ── Image Viewer ──────────────────────────────────────────────
function openViewer(src, label) {
    currentZoom = 1; currentRotate = 0;
    document.getElementById('viewer-img').src = src;
    document.getElementById('viewer-img').style.transform = '';
    document.getElementById('viewer-label').textContent = label;
    document.getElementById('image-viewer').classList.add('open');
}
function closeViewer() { document.getElementById('image-viewer').classList.remove('open'); document.getElementById('viewer-img').src = ''; }
function zoomIn()    { currentZoom = Math.min(currentZoom + 0.25, 4); applyTransform(); }
function zoomOut()   { currentZoom = Math.max(currentZoom - 0.25, 0.25); applyTransform(); }
function zoomReset() { currentZoom = 1; currentRotate = 0; applyTransform(); }
function rotateImg() { currentRotate = (currentRotate + 90) % 360; applyTransform(); }
function applyTransform() { document.getElementById('viewer-img').style.transform = 'scale(' + currentZoom + ') rotate(' + currentRotate + 'deg)'; }
document.getElementById('image-viewer').addEventListener('click', function(e) {
    if (e.target === this || e.target === document.querySelector('.viewer-body')) closeViewer();
});

// ── Toast ─────────────────────────────────────────────────────
function showToast(msg, type) {
    type = type || 'info';
    const icons = { success:'bi-check-circle-fill', error:'bi-x-circle-fill', info:'bi-info-circle-fill' };
    const colors = { success:'var(--success)', error:'var(--danger)', info:'var(--accent)' };
    const el = document.createElement('div');
    el.className = 'toast-item ' + type;
    el.innerHTML = '<i class="bi ' + icons[type] + '" style="color:' + colors[type] + '"></i> ' + msg;
    document.getElementById('toast-container').appendChild(el);
    setTimeout(function() { el.remove(); }, 4000);
}

// ── Utils ─────────────────────────────────────────────────────
function formatDate(d) {
    if (!d || d.length < 8) return d || '-';
    return d.substring(6,8) + '/' + d.substring(4,6) + '/' + d.substring(0,4);
}
function escHtml(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}


// ══ HN Config & Inputer ════════════════════════════════════════
let hnSepMode = 'N';   // 'Y' = แยกปี, 'N' = รวม
let patientCallerContext = null; // 'scan' | 'view'

async function initHnConfig() {
    try {
        const res = await fetch('/api/patient/config');
        const data = await res.json();
        hnSepMode = data.hnSep || 'N';
    } catch(e) { hnSepMode = 'N'; }
    renderHnInputer('scan-hn-inputer', 'scan');
    renderHnInputer('view-hn-inputer', 'view');
    renderHnInputer('add-pt-hn-inputer', 'addpt');
}

function renderHnInputer(containerId, ctx) {
    const el = document.getElementById(containerId);
    if (!el) return;
    if (hnSepMode === 'Y') {
        el.innerHTML = `
            <div class="d-flex gap-2 align-items-center">
                <input type="text" id="${ctx}-hn-main" class="form-control" maxlength="8"
                       placeholder="พิมพ์ปี+HN แล้วกด Enter" style="flex:3;font-family:monospace;letter-spacing:0.05em;"
                       onkeydown="hnMainKey(event,'${ctx}')">
                <span style="color:var(--text-muted);font-size:0.9rem;">-</span>
                <input type="text" id="${ctx}-hn-year" class="form-control" maxlength="2"
                       placeholder="ปี" style="flex:1;font-family:monospace;letter-spacing:0.05em;"
                       readonly tabindex="-1">
                <button class="btn-outline-custom" style="padding:0.4rem 0.6rem;white-space:nowrap;"
                        onclick="clearHnInputer('${ctx}')" title="ล้างค่า">
                    <i class="bi bi-x-circle"></i>
                </button>
                <button class="btn-primary-custom" style="padding:0.4rem 0.6rem;white-space:nowrap;"
                        onclick="openPatientSearch('${ctx}')">
                    <i class="bi bi-person-search"></i> ค้นหา
                </button>
            </div>`;
    } else {
        el.innerHTML = `
            <div class="d-flex gap-2">
                <input type="text" id="${ctx}-hn-main" class="form-control" maxlength="8"
                       placeholder="8 หลัก" style="font-family:monospace;letter-spacing:0.05em;"
                       onkeydown="if(event.key==='Enter') triggerHnSearch('${ctx}')">
                <button class="btn-outline-custom" style="padding:0.4rem 0.6rem;white-space:nowrap;"
                        onclick="clearHnInputer('${ctx}')" title="ล้างค่า">
                    <i class="bi bi-x-circle"></i>
                </button>
                <button class="btn-primary-custom" style="padding:0.4rem 0.6rem;white-space:nowrap;"
                        onclick="openPatientSearch('${ctx}')">
                    <i class="bi bi-person-search"></i> ค้นหา
                </button>
            </div>`;
    }
}

function hnMainKey(e, ctx) {
    if (e.key === 'Enter') {
        e.preventDefault();
        const input = document.getElementById(ctx + '-hn-main');
        if (!input) return;
        const val = input.value.trim();
        if (hnSepMode === 'Y' && val.length >= 3) {
            // split 2 หลักแรก → ช่องปี, ที่เหลือ → ช่อง HN
            const year = val.substring(0, 2);
            const hn = val.substring(2);
            input.value = hn;
            const yearInput = document.getElementById(ctx + '-hn-year');
            if (yearInput) yearInput.value = year;
        }
        triggerHnSearch(ctx);
    }
}

function hnMainBlur(ctx) {
    // ไม่ทำอะไร — split เฉพาะตอนกด Enter เท่านั้น
}

function getHnValue(ctx) {
    const main = (document.getElementById(ctx + '-hn-main') || {value:''}).value.trim();
    if (hnSepMode === 'Y') {
        const year = (document.getElementById(ctx + '-hn-year') || {value:''}).value.trim();
        return year + main;
    }
    return main;
}

function setHnValue(ctx, patId) {
    // patId is padded varchar(10), trim and parse
    const trimmed = patId.trim();
    const main = document.getElementById(ctx + '-hn-main');
    const yearEl = document.getElementById(ctx + '-hn-year');
    if (!main) return;
    if (hnSepMode === 'Y') {
        // split: last 6 = HN, first 2 = year
        if (trimmed.length > 6) {
            if (yearEl) yearEl.value = trimmed.substring(0, trimmed.length - 6);
            main.value = trimmed.substring(trimmed.length - 6);
        } else {
            main.value = trimmed;
        }
    } else {
        main.value = trimmed;
    }
}

function clearHnInputer(ctx) {
    const mainEl = document.getElementById(ctx + '-hn-main');
    const yearEl = document.getElementById(ctx + '-hn-year');
    if (mainEl) mainEl.value = '';
    if (yearEl) yearEl.value = '';
    const box = document.getElementById(ctx + '-patient-info');
    if (box) { box.style.display = 'none'; box.innerHTML = ''; box.style.background = ''; box.style.borderColor = ''; }
    // clear viewer treatment list if view context
    if (ctx === 'view') {
        document.getElementById('treatment-list').innerHTML = '<div class="empty-state"><i class="bi bi-search"></i><p>กรอก HN เพื่อค้นหา</p></div>';
        document.getElementById('chartpage-list').innerHTML = '<div class="empty-state"><i class="bi bi-hand-index"></i><p>เลือกรายการการรักษาด้านซ้าย</p></div>';
        document.getElementById('treat-count').textContent = '';
        document.getElementById('page-count').textContent = '';
    }
}

async function triggerHnSearch(ctx) {
    const hn = getHnValue(ctx).trim();
    if (!hn || ctx === 'addpt') return;
    const infoBoxId = ctx + '-patient-info';
    const box = document.getElementById(infoBoxId);
    if (!box) return;

    // show loading
    box.style.display = 'flex';
    box.innerHTML = '<span class="loading-spinner"></span><span style="font-size:0.82rem;color:var(--text-muted);margin-left:0.5rem;">กำลังค้นหา...</span>';

    try {
        const res = await fetch('/api/patient/search?field=PATID&keyword=' + encodeURIComponent(hn));
        const data = await res.json();

        // find exact match (trim both sides)
        const match = Array.isArray(data) ? data.find(p => (p.PATID||'').trim() === hn) : null;

        if (match) {
            const age = calcAgeFromBirth(match.BIRTHDATE || '');
            updatePatientInfoBox(infoBoxId, hn, match.NAME || '', age);
            // also auto-fill viewer search if context is view
            if (ctx === 'view') searchTreatments();
        } else {
            // show red not-found box
            box.style.display = 'flex';
            box.style.background = '#fef2f2';
            box.style.borderColor = '#fecaca';
            box.innerHTML =
                '<i class="bi bi-exclamation-circle-fill" style="color:#dc2626;font-size:1rem;flex-shrink:0;"></i>' +
                '<div style="flex:1;font-size:0.85rem;color:#dc2626;font-weight:500;">ไม่พบข้อมูลผู้ป่วย HN: ' + escHtml(hn) + '</div>';
        }
    } catch(e) {
        box.innerHTML = '<span style="font-size:0.82rem;color:#dc2626;">เกิดข้อผิดพลาด: ' + escHtml(e.message) + '</span>';
    }
}

// ══ Patient Search ═════════════════════════════════════════════
function openPatientSearch(ctx) {
    patientCallerContext = ctx;
    document.getElementById('pt-search-kw').value = '';
    document.getElementById('pt-search-result').innerHTML =
        '<div class="empty-state"><i class="bi bi-person-search"></i><p>กรอกคำค้นหาแล้วกด Enter</p></div>';
    openModal('modal-patient');
    setTimeout(() => document.getElementById('pt-search-kw').focus(), 100);
}

async function searchPatient() {
    const field = document.getElementById('pt-search-field').value;
    const kw = document.getElementById('pt-search-kw').value.trim();
    if (!kw) { showToast('กรุณากรอกคำค้นหา', 'error'); return; }
    document.getElementById('pt-search-result').innerHTML =
        '<div class="empty-state"><span class="loading-spinner"></span></div>';
    try {
        const res = await fetch('/api/patient/search?field=' + encodeURIComponent(field) + '&keyword=' + encodeURIComponent(kw));
        const data = await res.json();
        if (!Array.isArray(data) || data.length === 0) {
            document.getElementById('pt-search-result').innerHTML =
                '<div class="empty-state"><i class="bi bi-inbox"></i><p>ไม่พบข้อมูล</p></div>';
            return;
        }
        document.getElementById('pt-search-result').innerHTML =
            '<table class="result-table"><thead><tr>' +
            '<th>HN</th><th>ชื่อ-สกุล</th><th>เลขบัตร</th><th>อายุ</th>' +
            '</tr></thead><tbody>' +
            data.map(p => {
                const age = calcAgeFromBirth(p.BIRTHDATE||'');
                return '<tr class="clickable" onclick="selectPatient(\'' + (p.PATID||'').trim().replace(/'/g,"\\'") + '\',\'' + (p.NAME||'').replace(/'/g,"\\'") + '\',\'' + (p.BIRTHDATE||'') + '\')">' +
                    '<td style="font-family:monospace">' + escHtml((p.PATID||'').trim()) + '</td>' +
                    '<td>' + escHtml(p.NAME||'') + '</td>' +
                    '<td style="font-size:0.8rem">' + escHtml(p.JUMINNO||'') + '</td>' +
                    '<td>' + age + '</td>' +
                    '</tr>';
            }).join('') +
            '</tbody></table>';
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
}

function selectPatient(patId, name, birthDate) {
    const ctx = patientCallerContext;
    setHnValue(ctx, patId);
    const trimmed = patId.trim();
    const age = calcAgeFromBirth(birthDate || '');

    if (ctx === 'scan') {
        updatePatientInfoBox('scan-patient-info', trimmed, name, age);
    } else if (ctx === 'view') {
        updatePatientInfoBox('view-patient-info', trimmed, name, age);
    }
    closeModal('modal-patient');
}

function updatePatientInfoBox(boxId, hn, name, age) {
    const box = document.getElementById(boxId);
    if (!box) return;
    box.style.display = "flex";
    box.style.background = "";
    box.style.borderColor = "";
    // Use data attribute to avoid quoting issues in onclick
    box.setAttribute("data-clear-ctx", boxId);
    box.innerHTML =
        '<i class="bi bi-person-fill" style="font-size:1.1rem;color:#166534;flex-shrink:0;"></i>' +
        '<div style="flex:1;">' +
            '<div style="font-weight:600;font-size:0.88rem;color:#166534;">' + escHtml(name) + '</div>' +
            '<div style="font-size:0.78rem;color:#4b5563;">HN: ' + escHtml(hn) + ' &nbsp;|&nbsp; อายุ: ' + escHtml(age) + '</div>' +
        '</div>' +
        '<button class="pt-clear-btn" style="background:none;border:none;cursor:pointer;color:#9ca3af;font-size:0.9rem;padding:0;" title="ล้าง">' +
            '<i class="bi bi-x-circle"></i></button>';
    // Attach click handler via JS (no inline onclick)
    box.querySelector(".pt-clear-btn").addEventListener("click", function() {
        clearPatientInfo(boxId);
    });
}

function clearPatientInfo(boxId) {
    const box = document.getElementById(boxId);
    if (box) { box.style.display = 'none'; box.innerHTML = ''; }
    const ctx = boxId.startsWith('scan') ? 'scan' : 'view';
    const mainEl = document.getElementById(ctx + '-hn-main');
    const yearEl = document.getElementById(ctx + '-hn-year');
    if (mainEl) mainEl.value = '';
    if (yearEl) yearEl.value = '';
}

function calcAgeFromBirth(birthDate) {
    if (!birthDate || birthDate.length < 4) return '-';
    try {
        const year = parseInt(birthDate.substring(0, 4));
        return new Date().getFullYear() - year + ' ปี';
    } catch(e) { return '-'; }
}

// ══ Add Patient ════════════════════════════════════════════════
function showAddPatientForm() {
    const hnEl = document.getElementById('add-pt-hn-main');
    if (hnEl) hnEl.value = '';
    ['add-pt-name','add-pt-juminno','add-pt-birth'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    document.getElementById('add-pt-sex').value = '';
    openModal('modal-add-patient');
}

async function saveNewPatient() {
    const patId = getHnValue('addpt');
    const name = (document.getElementById('add-pt-name').value || '').trim();
    const sex = document.getElementById('add-pt-sex').value;
    const jumiNno = (document.getElementById('add-pt-juminno').value || '').trim();
    const birthRaw = document.getElementById('add-pt-birth').value; // YYYY-MM-DD
    const birthDate = birthRaw ? birthRaw.replace(/-/g,'') : ''; // → YYYYMMDD
    const userId = (document.getElementById('scan-userid')||{value:'DEMO'}).value||'DEMO';

    if (!patId) { showToast('กรุณากรอก HN', 'error'); return; }
    if (!name)  { showToast('กรุณากรอกชื่อ', 'error'); return; }

    // Pad patId to 10 chars to match varchar(10)
    const paddedPatId = patId.padEnd(10, ' ');

    try {
        const res = await fetch('/api/patient/insert', {
            method: 'POST', headers: {'Content-Type':'application/json'},
            body: JSON.stringify({ patId: paddedPatId, name, sex, jumiNno, birthDate, userId })
        });
        const data = await res.json();
        if (data.success) {
            showToast('เพิ่มผู้ป่วยสำเร็จ', 'success');
            closeModal('modal-add-patient');
            // refresh search if open
            const kw = document.getElementById('pt-search-kw').value;
            if (kw) searchPatient();
        } else showToast(data.error || 'ล้มเหลว', 'error');
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
}

// ── Expose all to global scope ────────────────────────────────
window.initHnConfig      = initHnConfig;
window.triggerHnSearch   = triggerHnSearch;
window.clearHnInputer    = clearHnInputer;
window.hnMainKey         = hnMainKey;
window.hnMainBlur        = hnMainBlur;
window.openPatientSearch = openPatientSearch;
window.searchPatient     = searchPatient;
window.selectPatient     = selectPatient;
window.clearPatientInfo  = clearPatientInfo;
window.showAddPatientForm = showAddPatientForm;
window.saveNewPatient    = saveNewPatient;
window.switchTab         = switchTab;
window.toggleUserMenu    = toggleUserMenu;
window.openModal         = openModal;
window.closeModal        = closeModal;
window.openProgramConfig = openProgramConfig;
window.loadConfig        = loadConfig;
window.saveConfig        = saveConfig;
window.openDetailMaster  = openDetailMaster;
window.loadTabTyp        = loadTabTyp;
window.loadTabMst        = loadTabMst;
window.selectTabRow      = selectTabRow;
window.selectDtlRow      = selectDtlRow;
window.selectDtsRow      = selectDtsRow;
window.closeCrudForm     = closeCrudForm;
window.showConfirm       = showConfirm;
window.showTabMstForm    = showTabMstForm;
window.editTabMstRow     = editTabMstRow;
window.saveTabMst        = saveTabMst;
window.deleteTabMstRow   = deleteTabMstRow;
window.showDtlMstForm    = showDtlMstForm;
window.editDtlMstRow     = editDtlMstRow;
window.saveDtlMst        = saveDtlMst;
window.deleteDtlMstRow   = deleteDtlMstRow;
window.showDtsMstForm    = showDtsMstForm;
window.editDtsMstRow     = editDtsMstRow;
window.saveDtsMst        = saveDtsMst;
window.deleteDtsMstRow   = deleteDtsMstRow;
window.handleFileSelect  = handleFileSelect;
window.handleDragOver    = handleDragOver;
window.handleDragLeave   = handleDragLeave;
window.handleDrop        = handleDrop;
window.removeFile        = removeFile;
window.submitScan        = submitScan;
window.searchTreatments  = searchTreatments;
window.loadChartPages    = loadChartPages;
window.clearViewer       = clearViewer;
window.openViewer        = openViewer;
window.closeViewer       = closeViewer;
window.zoomIn            = zoomIn;
window.zoomOut           = zoomOut;
window.zoomReset         = zoomReset;
window.rotateImg         = rotateImg;
});
