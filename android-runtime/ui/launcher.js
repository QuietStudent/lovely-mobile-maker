/* All screens are bundled. No fetch, analytics, CDN, or hosted service. */
(() => {
  'use strict';
  const root = document.getElementById('lmm-app');
  const native = window.LmmNative;
  const catalog = [
    {name:'Steamodded', title:'Steamodded', description:'The loader most Balatro mods need.'},
    {name:'Multiplayer', title:'Balatro Multiplayer', description:'Play with friends. Match their mod version.'},
    {name:'JokerDisplay', title:'Joker Display', description:'See what your Jokers will score.'},
    {name:'Blueprint', title:'Blueprint', description:'Plan your run with a practice tool.'},
    {name:'WhatsInMyFool', title:"What's In My Fool", description:'See which card The Fool will copy.'}
  ];
  let state = {selected:'0.9.0', versions:[], mods:[], ready:false};
  let screen = 'home', selectedMod = '', message = '', busy = false, dialog = '';
  const title = name => catalog.find(m => m.name === name)?.title || name;
  const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const button = (label, action, value = '', kind = '') => `<button type="button" class="cursor-interaction ${kind}" data-action="${action}" data-value="${escape(value)}" ${busy ? 'disabled' : ''}>${label}</button>`;
  const row = (label, sub, action, value, end = '›', extra = '') => button(`<span><strong>${escape(label)}</strong><small>${escape(sub)}</small></span><span class="lmm-chevron">${end}</span>`, action, value, `lmm-row ${extra}`);
  function remember() {
    if (native) return;
    window.openai?.setWidgetState?.({modelContent:{screen,lovely:state.selected,activeMods:state.mods.filter(m=>m.enabled).map(m=>m.name)},privateContent:{screen,selectedMod,selected:state.selected,mods:state.mods}})?.catch(()=>{});
  }
  function go(next, mod = '') {screen = next; selectedMod = mod; message = ''; dialog = ''; render(); remember();}
  function command(action, value = '') {
    if (busy) return;
    if (native) {native.command(action, String(value)); return;}
    demo(action, value);
  }
  function render() {
    const selected = state.mods.find(m=>m.name === selectedMod);
    let content = '';
    if (screen === 'home') {
      content = `<main class="lmm-home"><div class="lmm-brand"><div class="lmm-cards" aria-hidden="true"><span class="lmm-playing-card">♥</span><span class="lmm-playing-card">♠</span><span class="lmm-playing-card">♦</span></div><h1>Balatro</h1><p class="lmm-eyebrow">Mobile mods</p><p class="lmm-ready">${state.ready ? `Lovely ${escape(state.selected)} · ${state.mods.filter(m=>m.enabled).length} mods active` : 'Preparing your game…'}</p></div><div class="lmm-home-actions">${button('Launch Game','launch','','lmm-blue')}${button('Change Lovely Version','lovely','','lmm-gold')}${button('Edit Mods','mods','','lmm-green')}</div><p class="lmm-footnote">Your game. Your mods.<br>Everything managed on this device.</p></main>`;
    } else {
      const heading = {lovely:'Lovely Version',mods:'Edit Mods',add:'Add Mods',mod:title(selectedMod),github:'GitHub Mod',zip:'Import a ZIP'}[screen] || 'Mobile Mods';
      content = `<header class="lmm-toolbar">${button('‹ Back','back','','lmm-back')}<h2>${escape(heading)}</h2></header>`;
      if (screen === 'lovely') {
        content += `<p class="lmm-copy">Choose a version for your next game. Included versions work offline.</p><div class="lmm-stack">${state.versions.map(v=>row(`Lovely ${v}`,v === state.selected ? 'Selected for your next game' : 'Available on this device','select',v,v===state.selected?'✓':'Use',v===state.selected?'lmm-current':'')).join('')}</div><details><summary class="cursor-interaction">Download another Android build</summary><p>Optional: use an HTTPS catalog from an Android Lovely maintainer. The builds included above do not need this.</p>${button('Enter catalog URL','catalog','','lmm-blue')}</details>`;
      }
      if (screen === 'mods') {
        content += `<p class="lmm-copy">Tap a mod to change its version, turn it on or off, or restore a previous install.</p><div class="lmm-stack">${state.mods.length ? state.mods.map(m=>row(title(m.name),m.version,'mod',m.name,`<span class="lmm-state ${m.enabled?'':'lmm-off'}">${m.enabled?'On':'Off'}</span>`)).join('') : '<p class="lmm-copy">No managed mods yet. Add your first mod below.</p>'}</div>${button('＋ Add Mods','add','','lmm-green lmm-wide')}<p class="lmm-copy" style="margin-top:24px">Mod downloads need internet. Installed mods and backups stay on this device.</p>`;
      }
      if (screen === 'add') {
        content += `<p class="lmm-copy">Pick a mod, then choose its version. Dependencies are installed separately.</p><div class="lmm-stack">${catalog.map(m=>row(m.title,m.description,'browse',m.name)).join('')}</div><h3 class="lmm-section">Bring your own mod</h3><div class="lmm-stack">${button('Install from GitHub','github')}${button('Import a ZIP from this phone','zip')}</div>`;
      }
      if (screen === 'mod' && selected) {
        content += `<div class="lmm-detail"><strong>${escape(title(selected.name))}</strong><p>${escape(selected.version)}</p><p>${selected.enabled?'Enabled':'Disabled'} · ${selected.repo?'GitHub install':'Imported from a ZIP'}</p></div><div class="lmm-stack">${button('Change Version','browse',selected.name,'lmm-blue')}${selected.name==='Steamodded' ? '<p class="lmm-copy">Steamodded stays enabled because other mods need it.</p>' : button(selected.enabled?'Disable Mod':'Enable Mod','toggle',selected.name,'lmm-green')}${button('Restore Previous Version','restore',selected.name)}</div><details><summary class="cursor-interaction">Install details</summary><p>${escape(selected.repo || 'Local ZIP')}<br>${escape(selected.version)}</p></details>`;
      }
      if (screen === 'github') content += `<p class="lmm-copy">Paste the repository link for one Lua or Lovely mod. You can choose a release, tag, branch, or exact commit.</p>${button('Enter GitHub repository','githubNative','','lmm-blue lmm-wide')}`;
      if (screen === 'zip') content += `<p class="lmm-copy">Choose a downloaded mod ZIP using Android's file picker. Use the same folder name to update an existing mod.</p>${button('Choose a ZIP','zipNative','','lmm-blue lmm-wide')}`;
    }
    root.innerHTML = `${native?'':'<p class="lmm-preview-label">Design preview · Sample installs</p>'}${content}${dialog}<div class="lmm-status" role="status" aria-live="polite">${escape(message)}</div>`;
    root.setAttribute('aria-busy',String(busy));
  }
  function back() {if(busy)return true;if(screen==='home')return false;go(screen==='mod'||screen==='add'?'mods':screen==='github'||screen==='zip'?'add':'home');return true;}
  root.addEventListener('click', event => {
    const hit = event.target.closest('button[data-action]'); if(!hit||hit.disabled)return;
    const a = hit.dataset.action, v = hit.dataset.value;
    if (['lovely','mods','add','github','zip'].includes(a))go(a);
    else if(a==='mod')go('mod',v);
    else if(a==='back')back();
    else if(a==='closeDemo'){dialog='';render();}
    else if(a==='demoInstall'){const [name,version]=JSON.parse(v);const existing=state.mods.find(m=>m.name===name);if(existing){existing.previous=existing.version;existing.version=version;}else state.mods.push({name,version,enabled:true,repo:'Sample GitHub source',previous:''});go('mods');message=`Preview: ${title(name)} ${version} selected. No files downloaded.`;render();remember();}
    else command(a,v);
  });
  function demo(action,value) {
    if(action==='launch')message='Preview only: the Android app opens your installed Balatro game here.';
    if(action==='select'){state.selected=value;message=`Lovely ${value} selected for your next game.`;remember();}
    if(action==='toggle'){const m=state.mods.find(m=>m.name===value);m.enabled=!m.enabled;message=`${title(value)} ${m.enabled?'enabled':'disabled'} in this preview.`;remember();}
    if(action==='restore'){const m=state.mods.find(m=>m.name===value);if(m.previous){[m.version,m.previous]=[m.previous,m.version];message='Previous sample version restored.';}else message='No previous install in this preview.';remember();}
    if(action==='browse'){
      const versions = value==='WhatsInMyFool'?['Current source (main)']:['Sample release A','Sample release B (older)','Sample prerelease'];
      dialog=`<section class="lmm-demo-dialog" aria-label="Version picker"><h2>${escape(title(value))}</h2><p>${value==='WhatsInMyFool'?'This mod has no published releases. Current source resolves main to an exact commit.':'In the app, this list comes directly from GitHub and includes older releases and prereleases.'}</p>${versions.map(v=>button(escape(v),'demoInstall',JSON.stringify([value,v]))).join('')}${button('Cancel','closeDemo')}</section>`;
    }
    if(action==='githubNative')message='In the Android app, enter a GitHub URL and choose the release or source ref.';
    if(action==='zipNative')message="In the Android app, this opens your phone's local file picker.";
    if(action==='catalog')message='Optional catalog downloads are handled by the app. Bundled Lovely 0.9.0 and 0.10.0 need no catalog.';
    render();
  }
  window.LmmApp = {
    back,
    update(snapshot){state=snapshot;render();},
    status(text,isBusy=false){message=text;busy=isBusy;render();}
  };
  if(!native){
    const saved=window.openai?.widgetState?.privateContent;
    state={selected:saved?.selected||'0.9.0',versions:['0.9.0','0.10.0'],ready:true,mods:saved?.mods||[
      {name:'Steamodded',version:'Sample compatible release',enabled:true,repo:'Steamodded/smods'},
      {name:'Multiplayer',version:'Sample matching release',enabled:true,repo:'Balatro-Multiplayer/BalatroMultiplayer'},
      {name:'JokerDisplay',version:'Sample release',enabled:true,repo:'nh6574/JokerDisplay',previous:'Sample older release'},
      {name:'Blueprint',version:'Sample release',enabled:false,repo:'stupxd/Blueprint'}
    ]};
    if(saved?.screen&&['home','lovely','mods','add','mod','github','zip'].includes(saved.screen)){screen=saved.screen;selectedMod=saved.selectedMod||'';}
    window.addEventListener('openai:set_globals',event=>{const restored=event.detail?.globals?.widgetState?.privateContent;if(restored?.mods){state.mods=restored.mods;state.selected=restored.selected||'0.9.0';screen=restored.screen||'home';selectedMod=restored.selectedMod||'';render();}});
  }
  render();
  if(native)native.command('init','');
})();
