<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/stores";
  import { goto } from "$app/navigation";
  import { serverTimestamp, addDoc, collection, updateDoc, doc } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/stores/auth";
  import {
    fetchProfile,
    fetchSocialCounts,
    checkFollowStatus,
    toggleFollow,
    sendFollowRequest,
    cancelFollowRequest,
    fetchFollowers,
    fetchFollowing,
    fetchUserPosts,
    enrichPostsWithInteractions,
    fetchReadingList,
    updateProfile,
    checkUsernameAvailable,
    resolveUsernameToUid,
    syncUsernameToSupabase,
    uploadAvatar,
    uploadCoverPhoto,
  } from "$lib/services/profile.service";
  import { togglePostLike, togglePostSave, deletePost as deletePostService } from "$lib/services/post.service";
  import { updatePost } from "$lib/services/compose.service";
  import Modal         from "$lib/components/Modal.svelte";
  import QuoteCard     from "$lib/components/QuoteCard.svelte";
  import UserChip      from "$lib/components/UserChip.svelte";
  import InfiniteScroll from "$lib/components/InfiniteScroll.svelte";
  import TabBar        from "$lib/components/TabBar.svelte";
  import EmptyState    from "$lib/components/EmptyState.svelte";
  import PageTopBar    from "$lib/components/PageTopBar.svelte";
  import { ago }       from "$lib/utils/time";
  import { showToast } from "$lib/stores/ui.store";

  let uid = $state("");
  $effect(() => {
    const p = $page.params.uid;
    uid = p === "me" ? ($currentUser?.uid ?? "") : p;
  });

  // ── State ─────────────────────────────────────────────────────
  let user          = $state<any>(null);
  let loading       = $state(true);
  let notFound      = $state(false);
  let isMe          = $state(false);
  $effect(() => { isMe = !!uid && uid === $currentUser?.uid; });

  let posts         = $state<any[]>([]);
  let postsLoading  = $state(true);
  let hasMorePosts  = $state(false);
  let lastPostDoc   = $state<any>(null);

  let followersCount = $state(0);
  let followingCount = $state(0);
  let postsCount     = $state(0);
  let isFollowing    = $state(false);
  let followLoading  = $state(false);

  let isPrivate           = $state(false);
  let canSeeContent       = $state(true);
  let followRequestStatus = $state<"none"|"pending"|"accepted">("none");
  let privateBlockedMsg   = $state(false);

  $effect(() => {
    if (privateBlockedMsg) setTimeout(() => { privateBlockedMsg = false; }, 2500);
  });

  let selectedTab = $state(0);
  const tabs = ["Gönderiler", "Okuma Listesi", "Kitaplar & Seriler"];

  let showFollowers  = $state(false);
  let showFollowing  = $state(false);
  let followersList  = $state<any[]>([]);
  let followingList  = $state<any[]>([]);
  let listLoading    = $state(false);

  // Düzenle
  let editMode       = $state(false);
  let menuOpenId     = $state<string | null>(null);
  let editName       = $state("");
  let editUsername   = $state("");
  let editBio        = $state("");
  let editWebsite    = $state("");
  let editSaving     = $state(false);
  let editError      = $state("");
  let photoUploading = $state(false);
  let coverUploading = $state(false);

  // Okuma listesi
  let readingList = $state<Record<string, any[]>>({});

  // Rozetler
  const BADGE_CATALOG: Record<string, { emoji: string; title: string; desc: string }> = {
    first_book:      { emoji: '📖', title: 'İlk Kitap',              desc: 'İlk kitabını okudum olarak işaretledin' },
    bookworm:        { emoji: '🐛', title: 'Kitap Kurdu',            desc: '10 kitap okudun' },
    library_master:  { emoji: '🏛️', title: 'Kütüphane Ustası',      desc: '25 kitap okudun' },
    quote_collector: { emoji: '💬', title: 'Alıntı Koleksiyoncusu', desc: '5 alıntı paylaştın' },
    quote_master:    { emoji: '🏆', title: 'Alıntı Ustası',          desc: '25 alıntı paylaştın' },
    streak_7:        { emoji: '🔥', title: '7 Günlük Seri',          desc: '7 gün üst üste aktif oldun' },
    streak_30:       { emoji: '⚡', title: '30 Günlük Seri',         desc: '30 gün üst üste aktif oldun' },
    streak_100:      { emoji: '🌟', title: '100 Günlük Seri',        desc: '100 gün üst üste aktif oldun' },
  };
  let badgeIds         = $state<string[]>([]);
  let activeBadgeInfo  = $state<string | null>(null); // tıklanan badge id

  // ReadBooksSheet
  let showReadBooksSheet = $state(false);

  // Kitap ekleme
  let showAddBookModal = $state(false);
  let addBookTitle     = $state(""); let addBookSynopsis = $state("");
  let addBookGenre     = $state(""); let addBookYear     = $state("");
  let addBookPages     = $state(""); let addBookCover    = $state("");
  let addBookSaving    = $state(false); let addBookError = $state("");
  let libraryBooks     = $state<any[]>([]);
  let libraryLoading   = $state(false);
  // book_quotes — feed_post_id boş olanlar filtrelenir (Android toPost() mantığı)
  let userQuotes       = $state<any[]>([]);
  let quotesLoading    = $state(false);
  let showQuotesSheet  = $state(false);

  // Alıntı paylaşma
  let showShareQuoteModal     = $state(false);
  let shareQuoteText          = $state(""); let shareQuoteBook    = $state("");
  let shareQuoteAuthor        = $state(""); let shareQuoteBookId  = $state("");
  let shareQuoteDropOpen      = $state(false);
  let shareQuoteSelectedBook  = $state<any | null>(null);
  let shareQuoteSaving        = $state(false);

  // Gönderi düzenleme
  let editModalPost   = $state<any | null>(null);
  let editModalTitle  = $state(""); let editModalText  = $state("");
  let editModalSaving = $state(false);
  let editQuoteModal  = $state<any | null>(null);
  let editQuoteText   = $state(""); let editQuoteBook  = $state(""); let editQuoteAuthor = $state("");
  let editQuoteSaving = $state(false);

  let expandedIds = $state(new Set<string>());

  let _loaded = $state("");

  onMount(() => {
    document.addEventListener("click", () => { menuOpenId = null; });
  });

  $effect(() => {
    if (!uid || uid === _loaded) return;
    _loaded = uid;
    loading = true; postsLoading = true;
    posts = []; user = null; notFound = false;
    isPrivate = false; canSeeContent = true; followRequestStatus = "none";
    loadUser().then(() => {
      loadSocialCounts();
      if ($currentUser && !isMe) checkFollow();
      loadReadingListData();
      loadBadges(uid);
    });
  });

  $effect(() => {
    if (selectedTab === 2 && uid && libraryBooks.length === 0 && !libraryLoading) {
      loadLibraryBooks(uid);
    }
  });

  // Android'in ProfileScreens.kt copyProfileLink'i ile birebir aynı format:
  // kullanıcı adı varsa onunla, yoksa uid ile — sabit kanonik domain (platformdan
  // bağımsız, her zaman aynı paylaşılabilir link üretilsin diye location.origin
  // yerine hardcoded).
  async function copyProfileLink() {
    const handle = user?.username?.trim();
    const link = handle
      ? `https://heftreng.onrender.com/profile/${handle}`
      : `https://heftreng.onrender.com/profile/${uid}`;
    try {
      await navigator.clipboard.writeText(link);
      showToast("Link kopyalandı!");
    } catch {
      showToast("Kopyalanamadı.");
    }
  }

  async function loadUser() {
    loading = true;
    try {
      const u = await fetchProfile(uid);
      if (!u) {
        // Route parametresi gerçek bir uid değil olabilir — Android paylaşım
        // linkleri kullanıcı adıyla üretiliyor (/profile/{username}).
        // Kullanıcı adı olarak çöz; bulunursa uid'i güncelle — bu $effect'i
        // yeniden tetikler ve loadUser() gerçek uid ile tekrar çalışır.
        const resolvedUid = await resolveUsernameToUid(uid);
        if (resolvedUid && resolvedUid !== uid) { uid = resolvedUid; return; }
        notFound = true; return;
      }
      user = u;
      isPrivate = u.isPrivate ?? false;
    } catch(e) { notFound = true; }
    finally { loading = false; }
  }

  async function loadSocialCounts() {
    const c = await fetchSocialCounts(uid);
    followersCount = c.followers; followingCount = c.following;
    if (c.posts) postsCount = c.posts;
  }

  async function checkFollow() {
    if (!$currentUser) return;
    const res = await checkFollowStatus($currentUser.uid, uid, isPrivate);
    isFollowing = res.isFollowing;
    followRequestStatus = res.followRequestStatus;
    canSeeContent = isMe || !isPrivate || isFollowing;
    if (canSeeContent) loadPosts();
    else postsLoading = false;
  }

  $effect(() => {
    if (!loading && user && !isPrivate && !isMe && posts.length === 0 && postsLoading) {
      canSeeContent = true; loadPosts();
    }
    if (!loading && user && isMe && posts.length === 0 && postsLoading) {
      canSeeContent = true; loadPosts();
    }
  });

  async function loadPosts() {
    postsLoading = true;
    try {
      const res = await fetchUserPosts(uid);
      const { likeCounts, likedIds, savedIds } = await enrichPostsWithInteractions(
        res.posts.map(p => p.id), $currentUser?.uid,
      );
      posts = res.posts.map(p => ({
        ...p,
        likesCount: likeCounts[p.id] ?? (p as any).likesCount ?? 0,
        isLikedByMe: likedIds.has(p.id),
        isSavedByMe: savedIds.has(p.id),
      }));
      lastPostDoc = res.lastDoc;
      hasMorePosts = res.hasMore;
      postsCount = posts.length;
    } catch(e) { console.error(e); }
    finally { postsLoading = false; }
  }

  async function loadMorePosts() {
    if (!lastPostDoc || !hasMorePosts) return;
    try {
      const res = await fetchUserPosts(uid, lastPostDoc);
      const { likeCounts, likedIds, savedIds } = await enrichPostsWithInteractions(
        res.posts.map(p => p.id), $currentUser?.uid,
      );
      const enriched = res.posts.map(p => ({
        ...p,
        likesCount: likeCounts[p.id] ?? (p as any).likesCount ?? 0,
        isLikedByMe: likedIds.has(p.id),
        isSavedByMe: savedIds.has(p.id),
      }));
      posts = [...posts, ...enriched];
      lastPostDoc = res.lastDoc;
      hasMorePosts = res.hasMore;
    } catch(e) { console.error(e); }
  }

  async function loadReadingListData() {
    readingList = await fetchReadingList(uid);
  }

  async function loadBadges(targetUid: string) {
    try {
      const { data } = await supabase
        .from('user_badges')
        .select('badge_id')
        .eq('uid', targetUid);
      badgeIds = (data ?? []).map((r: any) => r.badge_id as string);
    } catch(e) { console.error('badges:', e); }
  }

  async function loadUserQuotes() {
    quotesLoading = true;
    try {
      const { data } = await supabase
        .from('book_quotes')
        .select('id,text,book_title,author_name,feed_post_id,likes_count,created_at,user_display_name,user_photo_url,book_id')
        .eq('uid', uid)
        .order('created_at', { ascending: false })
        .limit(50);
      // Android'deki return@forEach mantığı: feed_post_id boş olanları filtrele
      userQuotes = (data ?? []).filter((q: any) => q.feed_post_id && q.feed_post_id.trim() !== '');
    } catch(e) { console.error('loadUserQuotes:', e); }
    finally { quotesLoading = false; }
  }

  // ── Takip ─────────────────────────────────────────────────────
  async function onFollowBtnClick() {
    if (!$currentUser) { goto("/login"); return; }
    if (isPrivate && !isFollowing) {
      followLoading = true;
      try {
        if (followRequestStatus === "pending") {
          await cancelFollowRequest($currentUser.uid, uid);
          followRequestStatus = "none";
        } else {
          await sendFollowRequest($currentUser.uid, $currentUser.displayName ?? "", $currentUser.photoURL ?? "", uid);
          followRequestStatus = "pending";
        }
      } catch(e) { console.error(e); }
      finally { followLoading = false; }
      return;
    }
    followLoading = true;
    const was = isFollowing;
    isFollowing = !was;
    followersCount = Math.max(0, followersCount + (was ? -1 : 1));
    try {
      await toggleFollow(
        $currentUser.uid, $currentUser.displayName ?? "", $currentUser.photoURL ?? "",
        uid, user?.displayName ?? "", user?.photoURL ?? "", was,
      );
      canSeeContent = isMe || !isPrivate || isFollowing;
      if (canSeeContent && posts.length === 0) loadPosts();
    } catch(e) {
      isFollowing = was;
      followersCount = Math.max(0, followersCount + (was ? 1 : -1));
    }
    followLoading = false;
  }

  function followBtnLabel() {
    if (isFollowing) return "Takip Ediliyor";
    if (isPrivate && followRequestStatus === "pending") return "İstek Gönderildi";
    return "Takip Et";
  }
  function followBtnClass() {
    if (isFollowing) return "btn-follow following";
    if (isPrivate && followRequestStatus === "pending") return "btn-follow pending";
    return "btn-follow";
  }

  async function loadFollowersData() {
    listLoading = true;
    followersList = (await fetchFollowers(uid)).map(r => ({ from_uid: r.uid, from_name: r.name, from_photo: r.photo }));
    listLoading = false;
  }
  async function loadFollowingData() {
    listLoading = true;
    followingList = (await fetchFollowing(uid)).map(r => ({ target_uid: r.uid, target_name: r.name, target_photo: r.photo }));
    listLoading = false;
  }

  // ── Profil düzenle ────────────────────────────────────────────
  function openEdit() {
    editName = user?.displayName ?? ""; editUsername = user?.username ?? "";
    editBio = user?.bio ?? ""; editWebsite = user?.website ?? "";
    editError = ""; editMode = true;
  }

  async function saveProfile() {
    if (!$currentUser) return;
    editSaving = true; editError = "";
    try {
      if (editUsername !== user?.username) {
        const ok = await checkUsernameAvailable(editUsername, uid);
        if (!ok) { editError = "Bu kullanıcı adı zaten alınmış."; editSaving = false; return; }
      }
      await updateProfile(uid, {
        displayName: editName.trim(),
        username:    editUsername.trim().toLowerCase(),
        bio:         editBio.trim(),
        website:     editWebsite.trim(),
      });
      await syncUsernameToSupabase(uid, editUsername.trim().toLowerCase(), editName.trim(), user?.photoURL ?? "");
      user = { ...user, displayName: editName.trim(), username: editUsername.trim(), bio: editBio.trim(), website: editWebsite.trim() };
      editMode = false;
    } catch(e: any) { editError = e.message ?? "Bir hata oluştu."; }
    finally { editSaving = false; }
  }

  async function uploadPhoto(e: Event, type: "avatar" | "cover") {
    if (!$currentUser) return;
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (type === "avatar") photoUploading = true;
    else coverUploading = true;
    try {
      const url = type === "avatar"
        ? await uploadAvatar(uid, file)
        : await uploadCoverPhoto(uid, file);
      const field = type === "avatar" ? "photoURL" : "coverPhoto";
      await updateProfile(uid, { [field]: url });
      user = { ...user, [field]: url };
    } catch(e) { console.error(e); }
    finally {
      if (type === "avatar") photoUploading = false;
      else coverUploading = false;
    }
  }

  // ── Post eylemleri ────────────────────────────────────────────
  async function toggleSave(p: any) {
    if (!$currentUser) { goto("/login"); return; }
    const was = p.isSavedByMe;
    posts = posts.map(x => x.id === p.id ? { ...x, isSavedByMe: !was } : x);
    try {
      await togglePostSave(p.id, $currentUser.uid, was);
    } catch(e) { posts = posts.map(x => x.id === p.id ? { ...x, isSavedByMe: was } : x); }
  }

  async function toggleLike(p: any) {
    if (!$currentUser) { goto("/login"); return; }
    const was = p.isLikedByMe;
    posts = posts.map(x => x.id === p.id ? {
      ...x, isLikedByMe: !was,
      likesCount: Math.max(0, (x.likesCount ?? 0) + (was ? -1 : 1)),
    } : x);
    try {
      await togglePostLike(p.id, $currentUser.uid, $currentUser.displayName ?? "", $currentUser.photoURL ?? "", was);
    } catch(e) {
      posts = posts.map(x => x.id === p.id ? { ...x, isLikedByMe: was, likesCount: Math.max(0, (x.likesCount ?? 0) + (was ? 1 : -1)) } : x);
    }
  }

  async function deletePostHandler(p: any) {
    if (!$currentUser || $currentUser.uid !== p.uid) return;
    if (!confirm("Gönderiyi silmek istiyor musunuz?")) return;
    await deletePostService(p.id);
    posts = posts.filter(x => x.id !== p.id);
  }

  function sharePost(p: any) {
    menuOpenId = null;
    const url = window.location.origin + "/post/" + p.id;
    if (navigator.share) navigator.share({ title: p.displayName, url });
    else navigator.clipboard.writeText(url);
  }

  function openEditModal(p: any) {
    menuOpenId = null;
    if (p.quoteText) {
      editQuoteModal = p; editQuoteText = p.quoteText ?? "";
      editQuoteBook = p.bookName ?? ""; editQuoteAuthor = p.authorName ?? "";
    } else {
      editModalPost = p; editModalTitle = p.title ?? ""; editModalText = p.text ?? "";
    }
  }

  async function saveEditPost() {
    if (!editModalPost || !editModalText.trim()) return;
    editModalSaving = true;
    try {
      await updatePost(editModalPost.id, { text: editModalText.trim(), title: editModalTitle.trim() });
      posts = posts.map(p => p.id === editModalPost!.id ? { ...p, text: editModalText.trim(), title: editModalTitle.trim() } : p);
      editModalPost = null;
    } catch(e) { console.error(e); }
    finally { editModalSaving = false; }
  }

  async function saveEditQuote() {
    if (!editQuoteModal || !editQuoteText.trim()) return;
    editQuoteSaving = true;
    try {
      await updatePost(editQuoteModal.id, { quoteText: editQuoteText.trim(), bookName: editQuoteBook.trim(), authorName: editQuoteAuthor.trim() });
      posts = posts.map(p => p.id === editQuoteModal!.id ? { ...p, quoteText: editQuoteText.trim(), bookName: editQuoteBook.trim(), authorName: editQuoteAuthor.trim() } : p);
      editQuoteModal = null;
    } catch(e) { console.error(e); }
    finally { editQuoteSaving = false; }
  }

  function closeEditModal() { editModalPost = null; editModalSaving = false; }
  function closeEditQuoteModal() { editQuoteModal = null; editQuoteSaving = false; }

  // ── Kütüphane ─────────────────────────────────────────────────
  async function loadLibraryBooks(authorUid: string) {
    libraryLoading = true;
    try {
      const { data } = await supabase.from("library_books")
        .select("id,title,author_name,cover_img,publish_year,synopsis,genre,page_count,type,is_serial")
        .eq("author_uid", authorUid).order("created_at", { ascending: false });
      libraryBooks = data ?? [];
    } catch(e) { libraryBooks = []; }
    finally { libraryLoading = false; }
  }

  function openAddBook() {
    addBookTitle = ""; addBookSynopsis = ""; addBookGenre = "";
    addBookYear = ""; addBookPages = ""; addBookCover = ""; addBookError = "";
    showAddBookModal = true;
  }

  async function submitAddBook() {
    if (!addBookTitle.trim() || !$currentUser) return;
    addBookSaving = true; addBookError = "";
    try {
      const row: any = {
        title: addBookTitle.trim(), synopsis: addBookSynopsis.trim(),
        genre: addBookGenre.trim(), cover_img: addBookCover.trim(),
        author_uid: uid, author_name: user?.displayName ?? "",
      };
      if (addBookYear.trim())  row.publish_year = parseInt(addBookYear) || 0;
      if (addBookPages.trim()) row.page_count   = parseInt(addBookPages) || 0;
      const { data, error: err } = await supabase.from("library_books").insert(row).select().single();
      if (err) throw err;
      libraryBooks = [data, ...libraryBooks];
      showAddBookModal = false;
    } catch(e: any) { addBookError = e?.message ?? "Kitap eklenirken hata oluştu."; }
    finally { addBookSaving = false; }
  }

  // ── Alıntı paylaşma ───────────────────────────────────────────
  function openShareQuote() {
    shareQuoteText = ""; shareQuoteBook = "";
    shareQuoteAuthor = user?.displayName ?? ""; shareQuoteBookId = "";
    shareQuoteSelectedBook = null; shareQuoteDropOpen = false;
    showShareQuoteModal = true;
    if (libraryBooks.length === 0) loadLibraryBooks(uid);
  }

  async function submitShareQuote() {
    if (!shareQuoteText.trim() || !$currentUser) return;
    shareQuoteSaving = true;
    try {
      const finalBook   = shareQuoteSelectedBook?.title      ?? shareQuoteBook.trim();
      const finalAuthor = shareQuoteSelectedBook?.author_name ?? shareQuoteAuthor.trim();
      const finalCover  = shareQuoteSelectedBook?.cover_img   ?? "";
      const finalBookId = shareQuoteSelectedBook?.id          ?? shareQuoteBookId;
      await addDoc(collection(db, "feed"), {
        uid: $currentUser.uid, displayName: $currentUser.displayName ?? "",
        name: $currentUser.displayName ?? "", username: "", photoURL: $currentUser.photoURL ?? "",
        text: "", title: "", category: "", imgUrl: "", imageURL: "",
        quoteText: shareQuoteText.trim(), bookName: finalBook,
        authorName: finalAuthor, coverImg: finalCover, libraryBookId: finalBookId,
        type: "library_quote", visibility: "public", mentions: [],
        likes: 0, saves: 0, cmtCount: 0, reposts: 0, ts: serverTimestamp(),
      });
      showShareQuoteModal = false;
      goto("/feed");
    } catch(e) { console.error(e); }
    finally { shareQuoteSaving = false; }
  }

  // ── Yardımcılar ───────────────────────────────────────────────
  function toggleExpand(id: string) {
    const s = new Set(expandedIds);
    s.has(id) ? s.delete(id) : s.add(id);
    expandedIds = s;
  }
  function repostLabel(type: string) {
    if (type === "serial")    return "📖 Kitap";
    if (type === "blog")      return "📝 Blog";
    if (type === "kf_lesson") return "🇹🇷 Kurdî";
    return "📄 Bölüm";
  }
  function copyId(p: any) { menuOpenId = null; navigator.clipboard.writeText('#' + p.id); }
  // ago() → $lib/utils/time
</script>


<svelte:head>
  <title>{user?.displayName ?? "Profil"} — Heftreng</title>
</svelte:head>

<!-- Gizli hesap toast -->
{#if privateBlockedMsg}
  <div class="private-toast">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
    Bu hesap gizli
  </div>
{/if}

<main class="wrap">

  {#if loading}
    <div class="cover-sk"></div>
    <div class="header-sk">
      <div class="av-sk"></div>
      <div class="sk-lines">
        <div class="sk-line" style="width:50%"></div>
        <div class="sk-line" style="width:35%"></div>
        <div class="sk-line" style="width:70%;margin-top:8px"></div>
      </div>
    </div>

  {:else if notFound}
    <div class="not-found">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64" style="color:var(--muted)"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/><line x1="18" y1="9" x2="22" y2="9"/></svg>
      <h3>Bu hesap mevcut değil</h3>
      <p>Silinmiş veya askıya alınmış olabilir.</p>
      <button onclick={() => history.back()} class="back-btn">Geri Dön</button>
    </div>

  {:else}

    <!-- ── Kapak fotoğrafı ─────────────────────────────────────── -->
    <div class="cover-wrap">
      {#if user?.coverPhoto}
        <img src={user.coverPhoto} alt="" class="cover-img" />
      {:else}
        <div class="cover-placeholder"></div>
      {/if}
      {#if isMe && editMode}
        <label class="cover-edit-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
          {coverUploading ? "Yükleniyor..." : "Kapak Değiştir"}
          <input type="file" accept="image/*" style="display:none" onchange={(e) => uploadPhoto(e, 'cover')} />
        </label>
      {/if}
    </div>

    <!-- ── Profil başlığı ──────────────────────────────────────── -->
    <div class="profile-header">
      <!-- Avatar + butonlar satırı -->
      <div class="av-row">
        <div class="av-wrap">
          {#if isMe && editMode}
            <label class="av-edit">
              {#if user?.photoURL}
                <img src={user.photoURL} alt="" class="avatar" />
              {:else}
                <div class="avatar avatar-fallback">{(user?.displayName ?? "?")[0].toUpperCase()}</div>
              {/if}
              <div class="av-overlay">
                {#if photoUploading}
                  <div class="spinner"></div>
                {:else}
                  <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" width="18" height="18"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
                {/if}
              </div>
              <input type="file" accept="image/*" style="display:none" onchange={(e) => uploadPhoto(e, 'avatar')} />
            </label>
          {:else}
            <div class="av-ring">
              {#if user?.photoURL}
                <img src={user.photoURL} alt={user.displayName} class="avatar" />
              {:else}
                <div class="avatar avatar-fallback">{(user?.displayName ?? "?")[0].toUpperCase()}</div>
              {/if}
            </div>
          {/if}
        </div>

        <!-- Sağ taraf butonlar -->
        <div class="header-btns">
          {#if isMe}
            {#if editMode}
              <button class="btn-cancel" onclick={() => editMode = false}>İptal</button>
              <button class="btn-save" onclick={saveProfile} disabled={editSaving}>
                {editSaving ? "Kaydediliyor..." : "Kaydet"}
              </button>
            {:else}
              <button class="btn-edit" onclick={openEdit}>Profili Düzenle</button>
              <button class="btn-icon" aria-label="Profil linkini kopyala" onclick={copyProfileLink}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
              </button>
              <a href="/settings" class="btn-icon" aria-label="Ayarlar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
              </a>
            {/if}
          {:else}
            <button class="btn-icon" aria-label="Profil linkini kopyala" onclick={copyProfileLink}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            </button>
            <a href="/messages?uid={uid}" class="btn-icon" aria-label="Mesaj">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </a>
            <button
              class={followBtnClass()}
              onclick={onFollowBtnClick}
              disabled={followLoading}
            >
              {followBtnLabel()}
            </button>
          {/if}
        </div>
      </div>

      <!-- Düzenleme formu -->
      {#if editMode}
        <div class="edit-form">
          {#if editError}<p class="edit-error">{editError}</p>{/if}
          <div class="edit-field">
            <label>Ad Soyad</label>
            <input bind:value={editName} maxlength={50} placeholder="Adınız" />
          </div>
          <div class="edit-field">
            <label>Kullanıcı adı</label>
            <div class="input-prefix">
              <span>@</span>
              <input bind:value={editUsername} maxlength={30}
                oninput={(e) => editUsername = e.currentTarget.value.toLowerCase().replace(/[^a-z0-9_]/g,'')}
                placeholder="kullanici_adi" />
            </div>
          </div>
          <div class="edit-field">
            <label>Bio</label>
            <textarea bind:value={editBio} maxlength={200} rows={3} placeholder="Kendinizden bahsedin..."></textarea>
          </div>
          <div class="edit-field">
            <label>Website</label>
            <input bind:value={editWebsite} placeholder="https://..." />
          </div>
        </div>

      {:else}
        <!-- Profil bilgileri -->
        <div class="profile-info">
          <div class="name-row">
            <h1 class="display-name">{user?.displayName ?? ""}</h1>
            {#if isPrivate}
              <span class="private-badge" title="Gizli hesap">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="13" height="13"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                Gizli
              </span>
            {/if}
          </div>
          {#if user?.username}<p class="username">@{user.username}</p>{/if}
          {#if user?.bio}<p class="bio">{user.bio}</p>{/if}
          {#if user?.website}
            <a href={user.website} target="_blank" rel="noopener" class="website">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
              {user.website.replace(/^https?:\/\//, '')}
            </a>
          {/if}
        </div>

        <!-- İstatistikler — gizli hesapta sayılar gizlenir (takip etmiyorsa) -->
        <div class="stats-row">
          <button class="stat-item" onclick={() => null}>
            <span class="stat-num">{canSeeContent ? postsCount : "—"}</span>
            <span class="stat-lbl">Gönderi</span>
          </button>
          <button class="stat-item" onclick={async () => {
            if (!canSeeContent) { privateBlockedMsg = true; return; }
            showFollowers = true; await loadFollowers();
          }}>
            <span class="stat-num">{followersCount}</span>
            <span class="stat-lbl">Takipçi</span>
          </button>
          <button class="stat-item" onclick={async () => {
            if (!canSeeContent) { privateBlockedMsg = true; return; }
            showFollowing = true; await loadFollowing();
          }}>
            <span class="stat-num">{followingCount}</span>
            <span class="stat-lbl">Takip</span>
          </button>
          {#if (user?.xp ?? 0) > 0}
            <div class="stat-item">
              <span class="stat-num" style="color:#F59E0B">{user.xp}</span>
              <span class="stat-lbl">XP</span>
            </div>
          {/if}
        </div>

        <!-- Okuma özeti hero -->
        {#if canSeeContent && ((user?.booksRead ?? 0) > 0 || (user?.streak ?? 0) > 0)}
          <div class="reading-hero">
            <button class="rh-stat rh-stat-btn" onclick={() => showReadBooksSheet = true}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="18" height="18"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              <strong>{user?.booksRead ?? (readingList['okudum']?.length ?? 0)}</strong>
              <span>kitap okudum</span>
            </button>
            <div class="rh-div"></div>
            <button class="rh-stat rh-stat-btn" onclick={() => { showQuotesSheet = true; if (!userQuotes.length) loadUserQuotes(); }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="18" height="18"><path d="M6 17h3l2-4V7H5v6h3zm8 0h3l2-4V7h-6v6h3z"/></svg>
              <strong>{userQuotes.length || (user?.quotesShared ?? 0)}</strong>
              <span>alıntı</span>
            </button>
            <div class="rh-div"></div>
            <div class="rh-stat" style="color:#F59E0B">
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M13.5 0.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67z"/></svg>
              <strong>{user?.streak ?? 0}</strong>
              <span>gün streak</span>
            </div>
          </div>
        {/if}

        <!-- Rozetler (Android BadgesRow) -->
        {#if badgeIds.length > 0}
          <div class="badges-row">
            {#each badgeIds as bid (bid)}
              {@const b = BADGE_CATALOG[bid]}
              {#if b}
                <button class="badge-item" onclick={() => activeBadgeInfo = activeBadgeInfo === bid ? null : bid} title={b.title}>
                  <span class="badge-emoji">{b.emoji}</span>
                  <span class="badge-label">{b.title}</span>
                </button>
              {/if}
            {/each}
          </div>
          {#if activeBadgeInfo && BADGE_CATALOG[activeBadgeInfo]}
            <div class="badge-info-bar">
              <span class="badge-info-emoji">{BADGE_CATALOG[activeBadgeInfo].emoji}</span>
              <div>
                <strong>{BADGE_CATALOG[activeBadgeInfo].title}</strong>
                <p>{BADGE_CATALOG[activeBadgeInfo].desc}</p>
              </div>
              <button class="badge-info-close" onclick={() => activeBadgeInfo = null}>✕</button>
            </div>
          {/if}
        {/if}
      {/if}
    </div>

    <!-- ── Sticky sekmeler ─────────────────────────────────────── -->
    <div class="tabs-bar">
      {#each tabs as tab, i}
        <button class="tab" class:active={selectedTab === i} onclick={() => selectedTab = i}>
          {tab}
        </button>
      {/each}
      <div class="tab-indicator" style="transform: translateX({selectedTab * 100}%)"></div>
    </div>

    <!-- ── Gizli hesap — takipçi değil ───────────────────────── -->
    {#if !canSeeContent}
      <div class="locked-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="52" height="52" style="color:var(--muted)"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        <h3>Bu hesap gizli</h3>
        <p>Gönderileri görmek için takip et.</p>
        <button
          class={followBtnClass()}
          onclick={onFollowBtnClick}
          disabled={followLoading}
          style="margin-top:4px"
        >
          {followBtnLabel()}
        </button>
      </div>

    <!-- ── Tab içerikleri ──────────────────────────────────────── -->

    <!-- Gönderiler -->
    {:else if selectedTab === 0}
      {#if postsLoading && posts.length === 0}
        {#each Array(3) as _}
          <div class="post-sk">
            <div class="sk-av-sm"></div>
            <div class="sk-lines" style="flex:1">
              <div class="sk-line" style="width:50%"></div>
              <div class="sk-line" style="width:80%;margin-top:6px"></div>
            </div>
          </div>
        {/each}
      {:else if posts.length === 0}
        <EmptyState icon="📄" message="Henüz gönderi yok."
          actionLabel={isMe ? 'İlk gönderiyi yaz →' : ''}
          actionHref="/compose"
        />
      {:else}
        <div class="feed-list">
      {#each posts as p (p.id)}
          {@const isOwn      = p.uid === $currentUser?.uid}
          {@const isExpanded = expandedIds.has(p.id)}
          {@const isLongText = (p.text?.length ?? 0) > 280}

          <article class="card" onclick={() => window.location.href = '/post/' + p.id} role="button" tabindex="0">

            <!-- ── Kart başlığı ── -->
            <div class="card-head">
              <a href="/profile/{p.uid}" class="avatar-link" onclick={(e) => e.stopPropagation()}>
                <div class="avatar-ring">
                  {#if p.photoURL}
                    <img src={p.photoURL} alt={p.displayName} class="avatar-img" />
                  {:else}
                    <div class="avatar-fallback">{(p.displayName ?? "?")[0].toUpperCase()}</div>
                  {/if}
                </div>
              </a>
              <div class="meta">
                <a href="/profile/{p.uid}" class="display-name" onclick={(e) => e.stopPropagation()}>{p.displayName ?? "Anonim"}</a>
                <div class="meta-row">
                  {#if p.username}<span class="username">@{p.username}</span><span class="dot">·</span>{/if}
                  <span class="time">{ago(p.ts)}</span>
                </div>
              </div>
              <!-- 3 nokta menü -->
              <div class="menu-wrap" onclick={(e) => e.stopPropagation()}>
                <button class="menu-btn" onclick={(e) => { e.stopPropagation(); menuOpenId = menuOpenId === p.id ? null : p.id; }} aria-label="Seçenekler">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><circle cx="12" cy="5" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="12" cy="19" r="1.8"/></svg>
                </button>
                {#if menuOpenId === p.id}
                  <div class="dropdown">
                    {#if isOwn}
                      <button class="dropdown-item" onclick={() => openEditModal(p)}>
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                        Düzenle
                      </button>
                      <button class="dropdown-item danger" onclick={() => { menuOpenId = null; deletePost(p); }}>
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
                        Sil
                      </button>
                      <div class="dropdown-divider"></div>
                    {/if}
                    <button class="dropdown-item" onclick={() => { menuOpenId = null; sharePost(p); }}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                      Bağlantıyı Kopyala
                    </button>
                    <button class="dropdown-item" onclick={() => copyId(p)}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                      Gönderi ID'sini Kopyala
                    </button>
                  </div>
                {/if}
              </div>
            </div>

            <!-- ── İçerik ── -->
            <div class="card-body">
              {#if p.quoteText}
                <div class="quote-card" onclick={(e) => e.stopPropagation()}>
                  <span class="quote-mark">❝</span>
                  <div class="quote-inner">
                    <p class="quote-text">{p.quoteText}</p>
                    {#if p.bookName || p.authorName}
                      <div class="quote-source">
                        <div class="quote-cover">
                          {#if p.coverImg}<img src={p.coverImg} alt={p.bookName} />{:else}
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                          {/if}
                        </div>
                        <div>
                          {#if p.bookName}<span class="quote-book">{p.bookName}</span>{/if}
                          {#if p.authorName}<span class="quote-author">{p.authorName}</span>{/if}
                        </div>
                      </div>
                    {/if}
                  </div>
                </div>
              {/if}

              {#if p.category}<div class="category-chip">{p.category}</div>{/if}
              {#if p.title}<h2 class="post-title">{p.title}</h2>{/if}
              {#if p.text}
                <p class="post-text" class:clamped={isLongText && !isExpanded}>{p.text}</p>
                {#if isLongText}
                  <button class="read-more" onclick={(e) => { e.stopPropagation(); toggleExpand(p.id); }}>
                    {isExpanded ? "Daha az göster" : "Devamını oku"}
                  </button>
                {/if}
              {/if}

              {#if p.repostType && p.repostType !== "kf_achievement"}
                <div class="repost-embed" onclick={(e) => { e.stopPropagation(); window.location.href = '/post/' + p.repostId; }}>
                  <div class="repost-label">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
                    {repostLabel(p.repostType)}
                  </div>
                  {#if p.repostAuthor}
                    <div class="repost-author">
                      <div class="repost-av">
                        {#if p.repostAuthorPhoto}<img src={p.repostAuthorPhoto} alt={p.repostAuthor} />{:else}{p.repostAuthor[0].toUpperCase()}{/if}
                      </div>
                      <span>{p.repostAuthor}</span>
                    </div>
                  {/if}
                  {#if p.repostTitle || p.serialTitle}<p class="repost-title">{p.repostTitle || p.serialTitle}</p>{/if}
                  {#if p.chapterTitle}<p class="repost-chapter">{p.chapterTitle}</p>{/if}
                  {#if p.repostText}<p class="repost-text">{p.repostText}</p>{/if}
                  {#if p.repostImg || p.serialCover}<img src={p.repostImg || p.serialCover} alt="" class="repost-img" />{/if}
                </div>
              {/if}

              {#if p.repostType === "kf_achievement"}
                <div class="achievement-card">
                  <div class="achievement-inner">
                    <div class="achievement-top">
                      <span class="achievement-trophy">🏆</span>
                      <span class="achievement-level">Seviye {p.repostLevel}</span>
                    </div>
                    <div class="achievement-stats">
                      <div><span class="ach-num">{p.repostXp}</span><span class="ach-lbl">XP</span></div>
                      <div><span class="ach-num">{p.repostStreak}</span><span class="ach-lbl">Gün serisi</span></div>
                    </div>
                    <p class="achievement-caption">Kurdî öğrenme yolculuğunda harika ilerleme!</p>
                  </div>
                </div>
              {/if}
            </div>

            {#if p.imgUrl || p.imageURL}
              <img src={p.imgUrl || p.imageURL} alt="" class="post-img" onclick={(e) => e.stopPropagation()} />
            {/if}

            <!-- ── Aksiyonlar ── -->
            <div class="actions" onclick={(e) => e.stopPropagation()}>
              <button class="act-btn" class:liked={p.isLikedByMe} onclick={() => toggleLike(p)} aria-label="Beğen">
                {#if p.isLikedByMe}
                  <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                {:else}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                {/if}
              </button>
              {#if (p.likesCount ?? 0) > 0}
                <button class="likes-pill" onclick={(e) => { e.stopPropagation(); window.location.href = '/post/' + p.id; }} aria-label="Beğenenleri gör">
                  {p.likesCount} beğeni
                </button>
              {/if}

              <button class="act-btn" onclick={(e) => { e.stopPropagation(); window.location.href = '/post/' + p.id; }} aria-label="Yorum yap">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                {#if (p.commentsCount ?? 0) > 0}<span>{p.commentsCount}</span>{/if}
              </button>

              <button class="act-btn" onclick={() => sharePost(p)} aria-label="Paylaş">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
              </button>

              <div class="act-spacer"></div>

              <button class="act-btn save-btn" class:saved={p.isSavedByMe} onclick={() => toggleSave(p)} aria-label="Kaydet">
                {#if p.isSavedByMe}
                  <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
                {:else}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
                {/if}
              </button>
            </div>
          </article>
        {/each}

        {#if hasMorePosts}
          <div class="load-more-wrap">
            <button class="load-more-btn" onclick={loadMorePosts}>Daha fazla göster</button>
          </div>
        {/if}
      </div>
      {/if}

    <!-- Okuma listesi -->
    {:else if selectedTab === 1}
      {#if Object.keys(readingList).length === 0}
        <EmptyState icon="📚" message="Okuma listesi boş." />
      {:else}
        {#each [["okuyor", "📖 Şu an okuyor"], ["okuyacak", "📚 Okuyacak"], ["okudum", "✅ Okudum"]] as [status, label]}
          {#if readingList[status]?.length}
            <div class="rl-section">
              <h3 class="rl-header">{label} <span>{readingList[status].length}</span></h3>
              {#each readingList[status] as entry}
                <div class="rl-item">
                  {#if entry.cover_url}
                    <img src={entry.cover_url} alt={entry.book_name} class="rl-cover" />
                  {:else}
                    <div class="rl-cover rl-cover-ph">📖</div>
                  {/if}
                  <div class="rl-info">
                    <span class="rl-book">{entry.book_name ?? "—"}</span>
                    {#if entry.author_name}<span class="rl-author">{entry.author_name}</span>{/if}
                  </div>
                </div>
              {/each}
            </div>
          {/if}
        {/each}
      {/if}

    <!-- Kitaplar & Seriler -->
    {:else}
      <!-- Kitaplar sekmesi: $effect ile otomatik yüklenir -->

      <!-- Üst bar: başlık + kitap ekle butonu -->
      <div class="books-header">
        <span class="books-title">Kütüphane Kitapları</span>
        {#if isMe}
          <button class="add-book-btn" onclick={openAddBook}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="15" height="15"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Kitap Ekle
          </button>
        {/if}
      </div>

      {#if libraryLoading}
        <EmptyState icon="⏳" message="Yükleniyor..." />
      {:else if libraryBooks.length === 0}
          <EmptyState icon="📖" message="Henüz kitap eklenmemiş."
            actionLabel={isMe ? 'İlk kitabı ekle →' : ''}
            onAction={openAddBook}
          />
      {:else}
        <div class="books-grid">
          {#each libraryBooks as book (book.id)}
            <a href="/library/book/{book.id}" class="book-card">
              <div class="book-cover-wrap">
                {#if book.cover_img}
                  <img src={book.cover_img} alt={book.title} class="book-cover"/>
                {:else}
                  <div class="book-cover-ph">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                  </div>
                {/if}
              </div>
              <div class="book-info">
                {#if book.type === 'serial' || book.is_serial}
                  <span class="book-type-badge serial">Seri</span>
                {:else}
                  <span class="book-type-badge book">Kitap</span>
                {/if}
                <span class="book-title">{book.title}</span>
                {#if book.author_name}<span class="book-author">{book.author_name}</span>{/if}
                {#if book.genre}<span class="book-meta-chip">{book.genre}</span>{/if}
              </div>
            </a>
          {/each}
        </div>
      {/if}
    {/if}

    <div style="height:80px"></div>
  {/if}
</main>

<!-- ── Okunan Kitaplar Sheet (Android ReadBooksSheet) ────────── -->
{#if showReadBooksSheet}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="sheet-backdrop" onclick={() => showReadBooksSheet = false}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Okunan Kitaplar <span class="sheet-count">{readingList['okudum']?.length ?? 0}</span></span>
      <button class="sheet-close" onclick={() => showReadBooksSheet = false}>✕</button>
    </div>
    <div class="sheet-list">
      {#if !readingList['okudum']?.length}
        <p class="sheet-empty">Henüz okunan kitap yok.</p>
      {:else}
        {#each readingList['okudum'] as entry}
          <div class="rl-sheet-item">
            {#if entry.cover_url}
              <img src={entry.cover_url} alt={entry.book_name} class="rl-sheet-cover" />
            {:else}
              <div class="rl-sheet-cover rl-sheet-cover-ph">📖</div>
            {/if}
            <div class="rl-sheet-info">
              <span class="rl-sheet-title">{entry.book_name ?? '—'}</span>
              {#if entry.author_name}<span class="rl-sheet-author">{entry.author_name}</span>{/if}
            </div>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted);flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<!-- ── Alıntılar Sheet (Android UserQuotesSheet karşılığı) ─────── -->
{#if showQuotesSheet}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="modal-backdrop" onclick={() => showQuotesSheet = false}></div>
  <div class="quotes-sheet">
    <div class="quotes-sheet-handle"></div>
    <div class="quotes-sheet-header">
      <span class="quotes-sheet-title">Alıntılarım ({userQuotes.length})</span>
      <button class="sheet-close" onclick={() => showQuotesSheet = false}>✕</button>
    </div>
    <div class="quotes-sheet-divider"></div>

    {#if quotesLoading}
      <div class="quotes-sheet-empty">
        <div class="spinner"></div>
      </div>
    {:else if userQuotes.length === 0}
      <div class="quotes-sheet-empty">
        <p>Henüz alıntı yok</p>
      </div>
    {:else}
      <div class="quotes-sheet-list">
        {#each userQuotes as q (q.id)}
          <!-- Android: onClick → navigate library_book_detail/{bookId} -->
          <a
            href={q.book_id ? `/library/book/${q.book_id}` : `/post/${q.feed_post_id}`}
            class="quotes-sheet-item"
            onclick={() => showQuotesSheet = false}
          >
            <p class="qsi-text">"{q.text}"</p>
            <p class="qsi-meta">
              {[q.author_name, q.book_title].filter(Boolean).join(' · ')}
            </p>
          </a>
          <div class="quotes-sheet-divider"></div>
        {/each}
      </div>
    {/if}
    <div style="height:16px"></div>
  </div>
{/if}


<!-- ── Gönderi Düzenleme Modal ───────────────────────────────── -->
{#if editModalPost}
  <div class="modal-backdrop" onclick={closeEditModal}></div>
  <div class="modal-card">
    <div class="modal-header">
      <span class="modal-title">Gönderiyi Düzenle</span>
      <button class="sheet-close" onclick={closeEditModal}>✕</button>
    </div>
    <div class="modal-body">
      <label class="modal-label">Başlık</label>
      <input class="modal-input" placeholder="Başlık (opsiyonel)" bind:value={editModalTitle} maxlength={120}/>
      <label class="modal-label" style="margin-top:8px">Metin *</label>
      <textarea class="modal-textarea" rows={5} placeholder="Gönderi metni..." bind:value={editModalText}></textarea>
    </div>
    <div class="modal-actions">
      <button class="modal-cancel" onclick={closeEditModal}>İptal</button>
      <button class="modal-save" onclick={saveEditPost} disabled={!editModalText.trim() || editModalSaving}>
        {editModalSaving ? "Kaydediliyor..." : "Kaydet"}
      </button>
    </div>
  </div>
{/if}

<!-- ── Alıntı Düzenleme Modal ─────────────────────────────────── -->
{#if editQuoteModal}
  <div class="modal-backdrop" onclick={closeEditQuoteModal}></div>
  <div class="modal-card">
    <div class="modal-header">
      <span class="modal-title">Alıntıyı Düzenle</span>
      <button class="sheet-close" onclick={closeEditQuoteModal}>✕</button>
    </div>
    <div class="modal-body">
      <label class="modal-label">Alıntı metni *</label>
      <textarea class="modal-textarea quote-ta" rows={4} placeholder="Alıntı metni..." bind:value={editQuoteText}></textarea>
      <label class="modal-label" style="margin-top:8px">Kitap adı</label>
      <input class="modal-input" placeholder="Kitap adı..." bind:value={editQuoteBook}/>
      <label class="modal-label" style="margin-top:8px">Yazar adı</label>
      <input class="modal-input" placeholder="Yazar adı..." bind:value={editQuoteAuthor}/>
    </div>
    <div class="modal-actions">
      <button class="modal-cancel" onclick={closeEditQuoteModal}>İptal</button>
      <button class="modal-save" onclick={saveEditQuote} disabled={!editQuoteText.trim() || editQuoteSaving}>
        {editQuoteSaving ? "Kaydediliyor..." : "Kaydet"}
      </button>
    </div>
  </div>
{/if}

<!-- ── Kitap Ekleme Modal (Android AdminAddBookDialog) ────────── -->
{#if showAddBookModal}
  <div class="modal-backdrop" onclick={closeAddBook}></div>
  <div class="modal-card">
    <div class="modal-header">
      <span class="modal-title">Kitap Ekle</span>
      <button class="sheet-close" onclick={closeAddBook}>✕</button>
    </div>
    <div class="modal-body">
      <label class="modal-label">Kitap Adı *</label>
      <input class="modal-input" placeholder="Kitap adı..." bind:value={addBookTitle}/>

      <label class="modal-label" style="margin-top:8px">Özet (opsiyonel)</label>
      <textarea class="modal-textarea" rows={3} placeholder="Kısa açıklama..." bind:value={addBookSynopsis}></textarea>

      <div class="modal-row">
        <div class="modal-col">
          <label class="modal-label">Tür</label>
          <input class="modal-input" placeholder="Roman, Şiir..." bind:value={addBookGenre}/>
        </div>
        <div class="modal-col">
          <label class="modal-label">Yayın Yılı</label>
          <input class="modal-input" type="number" placeholder="2024" bind:value={addBookYear}/>
        </div>
      </div>

      <div class="modal-row">
        <div class="modal-col">
          <label class="modal-label">Sayfa Sayısı</label>
          <input class="modal-input" type="number" placeholder="320" bind:value={addBookPages}/>
        </div>
        <div class="modal-col">
          <label class="modal-label">Kapak URL</label>
          <input class="modal-input" placeholder="https://..." bind:value={addBookCover}/>
        </div>
      </div>

      {#if addBookCover}
        <div class="cover-preview-wrap">
          <img src={addBookCover} alt="Kapak önizleme" class="cover-preview"
               onerror={(e: any) => { e.target.style.display='none'; }}/>
        </div>
      {/if}

      {#if addBookError}
        <p class="modal-error">{addBookError}</p>
      {/if}
    </div>
    <div class="modal-actions">
      <button class="modal-cancel" onclick={closeAddBook}>İptal</button>
      <button
        class="modal-save"
        onclick={submitAddBook}
        disabled={!addBookTitle.trim() || addBookSaving}
      >
        {addBookSaving ? "Ekleniyor..." : "Kitabı Ekle"}
      </button>
    </div>
  </div>
{/if}

<!-- ── Alıntı Paylaşma Modal (Android AuthorQuoteDialog) ──────── -->
{#if showShareQuoteModal}
  <div class="modal-backdrop" onclick={closeShareQuote}></div>
  <div class="modal-card">
    <div class="modal-header">
      <span class="modal-title">Alıntı Paylaş</span>
      <button class="sheet-close" onclick={closeShareQuote}>✕</button>
    </div>
    <div class="modal-body">
      <!-- Alıntı metni -->
      <label class="modal-label">Alıntı *</label>
      <textarea
        class="modal-textarea quote-ta"
        rows={4}
        placeholder="Alıntı metni..."
        bind:value={shareQuoteText}
      ></textarea>

      <!-- Yazar adı -->
      <label class="modal-label" style="margin-top:8px">Yazar adı</label>
      <input class="modal-input" placeholder="Yazar..." bind:value={shareQuoteAuthor}/>

      <!-- Kitap seç — kütüphane dropdown (Android AuthorQuoteDialog mantığı) -->
      <label class="modal-label" style="margin-top:8px">Kitap seç</label>

      {#if shareQuoteSelectedBook}
        <div class="selected-book-row">
          {#if shareQuoteSelectedBook.cover_img}
            <img src={shareQuoteSelectedBook.cover_img} alt={shareQuoteSelectedBook.title} class="selected-book-cover"/>
          {:else}
            <div class="selected-book-cover no-cover">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            </div>
          {/if}
          <div class="selected-book-info">
            <span class="selected-book-title">{shareQuoteSelectedBook.title}</span>
            {#if shareQuoteSelectedBook.author_name}
              <span class="selected-book-author">{shareQuoteSelectedBook.author_name}</span>
            {/if}
            <span class="library-badge">✓ Kütüphane bağlandı</span>
          </div>
          <button class="clear-book-btn" onclick={() => { shareQuoteSelectedBook = null; shareQuoteBook = ""; }}>✕</button>
        </div>
      {:else if libraryBooks.length > 0}
        <!-- Kütüphaneden seç dropdown -->
        <div class="lib-book-list">
          {#each libraryBooks as book (book.id)}
            <button class="lib-book-item" onclick={() => { shareQuoteSelectedBook = book; shareQuoteBook = book.title; if (!shareQuoteAuthor) shareQuoteAuthor = book.author_name ?? ""; }}>
              {#if book.cover_img}
                <img src={book.cover_img} alt={book.title} class="lib-book-cover"/>
              {:else}
                <div class="lib-book-cover no-cover">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="12" height="12"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                </div>
              {/if}
              <div class="lib-book-text">
                <span class="lib-book-title">{book.title}</span>
                {#if book.publish_year > 0}<span class="lib-book-meta">{book.publish_year}</span>{/if}
              </div>
            </button>
          {/each}
        </div>
        <p class="or-divider">— veya kitap adını yaz —</p>
        <input class="modal-input" placeholder="Kitap adı..." bind:value={shareQuoteBook}/>
      {:else}
        <!-- Kütüphanede kitap yok, manuel giriş -->
        <input class="modal-input" placeholder="Kitap adı (opsiyonel)..." bind:value={shareQuoteBook}/>
        {#if isMe}
          <p class="hint-text">Kütüphane'ye kitap ekleyerek otomatik bağlayabilirsin.</p>
        {/if}
      {/if}
    </div>
    <div class="modal-actions">
      <button class="modal-cancel" onclick={closeShareQuote}>İptal</button>
      <button
        class="modal-save"
        onclick={submitShareQuote}
        disabled={!shareQuoteText.trim() || shareQuoteSaving}
      >
        {shareQuoteSaving ? "Paylaşılıyor..." : "Paylaş"}
      </button>
    </div>
  </div>
{/if}

<!-- ── Takipçiler sheet ────────────────────────────────────────── -->
{#if showFollowers}
  <div class="sheet-backdrop" onclick={() => showFollowers = false}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Takipçiler <span class="sheet-count">{followersCount}</span></span>
      <button class="sheet-close" onclick={() => showFollowers = false}>✕</button>
    </div>
    <div class="sheet-list">
      {#if listLoading}
        <div class="sheet-loading"><div class="spinner"></div></div>
      {:else if followersList.length === 0}
        <p class="sheet-empty">Henüz takipçi yok.</p>
      {:else}
        {#each followersList as f}
          <a href="/profile/{f.from_uid}" class="user-row" onclick={() => showFollowers = false}>
            <div class="user-av">
              {#if f.from_photo}<img src={f.from_photo} alt={f.from_name} />{:else}<span>{(f.from_name ?? "?")[0].toUpperCase()}</span>{/if}
            </div>
            <span class="user-name">{f.from_name ?? "Anonim"}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<!-- ── Takip edilenler sheet ──────────────────────────────────── -->
{#if showFollowing}
  <div class="sheet-backdrop" onclick={() => showFollowing = false}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Takip Edilenler <span class="sheet-count">{followingCount}</span></span>
      <button class="sheet-close" onclick={() => showFollowing = false}>✕</button>
    </div>
    <div class="sheet-list">
      {#if listLoading}
        <div class="sheet-loading"><div class="spinner"></div></div>
      {:else if followingList.length === 0}
        <p class="sheet-empty">Henüz takip edilen yok.</p>
      {:else}
        {#each followingList as f}
          <a href="/profile/{f.target_uid}" class="user-row" onclick={() => showFollowing = false}>
            <div class="user-av">
              {#if f.target_photo}<img src={f.target_photo} alt={f.target_name} />{:else}<span>{(f.target_name ?? "?")[0].toUpperCase()}</span>{/if}
            </div>
            <span class="user-name">{f.target_name ?? "Anonim"}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<style>
.wrap { max-width: 600px; margin: 0 auto; padding-bottom: 80px; overflow-x: clip; }

/* Kapak */
.cover-wrap { position: relative; width: 100%; height: 140px; background: var(--surface-var); overflow: hidden; margin-left: calc(-50vw + 50%); margin-right: calc(-50vw + 50%); }
.cover-img { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { width: 100%; height: 100%; background: linear-gradient(135deg, color-mix(in srgb,var(--primary) 20%,transparent), color-mix(in srgb,var(--primary) 8%,transparent)); }
.cover-edit-btn { position: absolute; bottom: 8px; right: 10px; display: flex; align-items: center; gap: 6px; padding: 6px 12px; background: rgba(0,0,0,0.55); color: #fff; border-radius: 20px; font-size: 12px; font-weight: 500; cursor: pointer; }

/* Profil başlığı */
.profile-header { padding: 0 16px 12px; background: var(--card); border-bottom: 1px solid var(--divider); position: relative; z-index: 1; }

/* Avatar satırı */
.av-row { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 12px; }
.av-wrap { margin-top: -28px; }
.av-ring { width: 78px; height: 78px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), color-mix(in srgb,var(--primary) 60%,purple)); padding: 2px; display: flex; align-items: center; justify-content: center; border: 3px solid var(--card); }
.avatar { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.avatar-fallback { background: var(--surface-var); display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700; color: var(--on-bg); }
.av-edit { position: relative; cursor: pointer; display: block; width: 78px; height: 78px; border-radius: 50%; margin-top: -28px; border: 3px solid var(--card); }
.av-edit img, .av-edit .avatar-fallback { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.av-overlay { position: absolute; inset: 0; border-radius: 50%; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; }

/* Header butonlar */
.header-btns { display: flex; align-items: center; gap: 8px; padding-bottom: 4px; }
.btn-edit { padding: 8px 16px; border: 1.5px solid var(--divider); border-radius: 10px; background: none; color: var(--on-bg); font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit; transition: background 0.15s; }
.btn-edit:hover { background: var(--surface-var); }
.btn-save { padding: 8px 16px; border: none; border-radius: 10px; background: var(--primary); color: #fff; font-size: 13px; font-weight: 700; cursor: pointer; font-family: inherit; }
.btn-save:disabled { opacity: 0.6; }
.btn-cancel { padding: 8px 12px; border: 1.5px solid var(--divider); border-radius: 10px; background: none; color: var(--muted); font-size: 13px; cursor: pointer; font-family: inherit; }
.btn-icon { width: 36px; height: 36px; border-radius: 10px; background: var(--surface-var); border: none; display: flex; align-items: center; justify-content: center; color: var(--on-bg); text-decoration: none; cursor: pointer; transition: background 0.15s; }
.btn-icon:hover { background: var(--divider); }
.btn-follow { padding: 8px 18px; border: none; border-radius: 10px; background: #F59E0B; color: #000; font-size: 13px; font-weight: 700; cursor: pointer; font-family: inherit; transition: background 0.15s, color 0.15s; }
.btn-follow.following { background: var(--surface-var); color: var(--on-bg); border: 1.5px solid var(--divider); }
.btn-follow.pending { background: var(--surface-var); color: var(--on-bg); border: 1.5px solid var(--divider); }
.btn-follow:disabled { opacity: 0.6; }

/* Ad + gizli badge */
.name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 2px; }
.profile-info .display-name { font-size: 20px; font-weight: 800; color: var(--on-bg); margin: 0; }
.private-badge { display: inline-flex; align-items: center; gap: 3px; font-size: 11px; font-weight: 600; color: var(--muted); background: var(--surface-var); border: 1px solid var(--divider); border-radius: 99px; padding: 2px 8px; }

/* Profil bilgileri */
.profile-info { margin-bottom: 12px; }
.username { font-size: 13px; color: var(--muted); margin: 0 0 6px; }
.bio { font-size: 14px; color: var(--on-bg); line-height: 1.6; margin: 0 0 6px; white-space: pre-wrap; }
.website { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #F59E0B; text-decoration: none; }
.website:hover { text-decoration: underline; }

/* İstatistikler */
.stats-row { display: flex; gap: 20px; margin-bottom: 12px; }
.stat-item { display: flex; flex-direction: column; align-items: center; background: none; border: none; cursor: pointer; padding: 0; font-family: inherit; }
.stat-num { font-size: 18px; font-weight: 800; color: var(--on-bg); }
.stat-lbl { font-size: 11px; color: var(--primary); font-weight: 500; }

/* Okuma hero */
.reading-hero { display: flex; align-items: center; justify-content: space-evenly; background: linear-gradient(135deg, color-mix(in srgb,var(--primary) 12%,transparent), color-mix(in srgb,#F59E0B 8%,transparent)); border-radius: 14px; padding: 14px 8px; margin: 4px 0 8px; }
.rh-stat { display: flex; flex-direction: column; align-items: center; gap: 3px; font-size: 11px; color: var(--muted); }
.rh-stat strong { font-size: 16px; font-weight: 800; color: var(--on-bg); }
.rh-div { width: 1px; height: 32px; background: var(--divider); }

/* Düzenleme formu */
.edit-form { display: flex; flex-direction: column; gap: 12px; margin-top: 4px; }
.edit-error { background: rgba(239,68,68,0.1); color: #ef4444; padding: 8px 12px; border-radius: 8px; font-size: 13px; }
.edit-field { display: flex; flex-direction: column; gap: 4px; }
.edit-field label { font-size: 12px; font-weight: 600; color: var(--muted); }
.edit-field input, .edit-field textarea {
  border: 1.5px solid var(--divider); border-radius: 10px;
  padding: 10px 12px; font-size: 14px; background: var(--surface-var);
  color: var(--on-bg); font-family: inherit; outline: none; resize: vertical;
}
.edit-field input:focus, .edit-field textarea:focus { border-color: var(--primary); }
.input-prefix { display: flex; align-items: center; border: 1.5px solid var(--divider); border-radius: 10px; background: var(--surface-var); overflow: hidden; }
.input-prefix span { padding: 10px 0 10px 12px; color: var(--muted); font-size: 14px; }
.input-prefix input { flex: 1; border: none; background: none; padding: 10px 12px 10px 4px; font-size: 14px; color: var(--on-bg); font-family: inherit; outline: none; }

/* Sekmeler */
.tabs-bar { display: flex; position: sticky; top: 52px; background: var(--surface); border-bottom: 1px solid var(--divider); z-index: 9; overflow: hidden; }
.tab { flex: 1; padding: 13px 4px; font-size: 13px; font-weight: 500; color: var(--muted); background: none; border: none; cursor: pointer; position: relative; z-index: 1; transition: color 0.2s; font-family: inherit; }
.tab.active { color: var(--on-bg); font-weight: 700; }
.tab-indicator { position: absolute; bottom: 0; left: 0; width: 33.33%; height: 2.5px; background: linear-gradient(90deg, var(--primary), color-mix(in srgb,var(--primary) 60%,purple)); border-radius: 2px 2px 0 0; transition: transform 0.25s cubic-bezier(.4,0,.2,1); pointer-events: none; }

/* Gizli hesap — locked state */
.locked-state { display: flex; flex-direction: column; align-items: center; padding: 56px 24px; gap: 12px; text-align: center; }
.locked-state h3 { font-size: 17px; font-weight: 700; color: var(--on-bg); margin: 0; }
.locked-state p { font-size: 13px; color: var(--muted); margin: 0; }

/* Private toast */
.private-toast { position: fixed; bottom: 90px; left: 50%; transform: translateX(-50%); background: var(--surface); border: 1px solid var(--divider); border-radius: 99px; padding: 10px 18px; font-size: 13px; font-weight: 600; color: var(--on-bg); display: flex; align-items: center; gap: 6px; box-shadow: 0 4px 16px rgba(0,0,0,0.16); z-index: 500; white-space: nowrap; animation: fadeup 0.2s ease; }
@keyframes fadeup { from { opacity: 0; transform: translateX(-50%) translateY(8px); } to { opacity: 1; transform: translateX(-50%) translateY(0); } }

/* Post kartları */
.feed-list { display: flex; flex-direction: column; gap: 8px; padding: 8px 12px; }
.card { background: var(--card); border-radius: 18px; border: 0.7px solid var(--divider); padding: 14px 15px 10px; cursor: pointer; transition: border-color 0.15s; display: block; text-align: left; width: 100%; }
.card:hover { border-color: color-mix(in srgb, var(--primary) 30%, var(--divider)); }
.card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.avatar-link { flex-shrink: 0; text-decoration: none; }
.avatar-ring { width: 44px; height: 44px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), color-mix(in srgb, var(--primary) 60%, purple)); padding: 1.5px; display: flex; align-items: center; justify-content: center; }
.avatar-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.avatar-fallback { width: 100%; height: 100%; border-radius: 50%; background: var(--surface-var); color: var(--on-bg); font-size: 16px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.meta { flex: 1; min-width: 0; }
.display-name { font-size: 14px; font-weight: 700; color: var(--on-bg); text-decoration: none; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.display-name:hover { text-decoration: underline; }
.meta-row { display: flex; align-items: center; gap: 4px; margin-top: 1px; }
.username, .time { font-size: 12px; color: var(--muted); }
.dot { font-size: 12px; color: var(--muted); }
.menu-wrap { position: relative; }
.menu-btn { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--muted); background: none; border: none; cursor: pointer; transition: background 0.15s; }
.menu-btn:hover { background: var(--surface-var); }
.dropdown { position: absolute; right: 0; top: calc(100% + 4px); background: var(--surface); border: 1px solid var(--divider); border-radius: 14px; min-width: 210px; box-shadow: 0 8px 24px rgba(0,0,0,0.14); overflow: hidden; z-index: 200; }
.dropdown-item { display: flex; align-items: center; gap: 10px; padding: 11px 14px; font-size: 13px; color: var(--on-surface); background: none; border: none; cursor: pointer; width: 100%; text-align: left; text-decoration: none; font-family: inherit; transition: background 0.1s; }
.dropdown-item:hover { background: var(--surface-var); }
.dropdown-item.danger { color: var(--error, #ef4444); }
.dropdown-divider { height: 1px; background: var(--divider); }
.card-body { margin-bottom: 2px; }
.category-chip { display: inline-block; background: color-mix(in srgb, var(--primary) 12%, transparent); color: var(--primary); font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 99px; margin-bottom: 6px; }
.post-title { font-size: 16px; font-weight: 700; color: var(--on-bg); line-height: 1.35; margin-bottom: 5px; }
.post-text { font-size: 15px; color: var(--on-bg); line-height: 1.65; white-space: pre-wrap; margin-bottom: 4px; }
.post-text.clamped { display: -webkit-box; -webkit-line-clamp: 6; -webkit-box-orient: vertical; overflow: hidden; }
.read-more { background: none; border: none; color: var(--primary); font-size: 13px; font-weight: 600; cursor: pointer; padding: 0; margin-bottom: 8px; font-family: inherit; }
.post-img { width: 100%; border-radius: 12px; max-height: 320px; object-fit: cover; margin-bottom: 10px; display: block; }
.quote-card { position: relative; background: linear-gradient(135deg,color-mix(in srgb,#F59E0B 8%,transparent),color-mix(in srgb,#9B72F5 6%,transparent)); border: 1px solid color-mix(in srgb,#F59E0B 35%,transparent); border-radius: 14px; padding: 14px 14px 12px; margin-bottom: 10px; overflow: hidden; }
.quote-mark { position: absolute; top: -6px; left: 6px; font-size: 52px; color: color-mix(in srgb,#F59E0B 15%,transparent); font-weight: 900; line-height: 1; pointer-events: none; font-family: Georgia,serif; }
.quote-inner { padding-left: 8px; position: relative; }
.quote-text { font-size: 14px; font-style: italic; color: var(--on-surface); line-height: 1.6; font-weight: 500; margin-bottom: 10px; }
.quote-source { display: flex; align-items: center; gap: 8px; }
.quote-cover { width: 28px; height: 42px; border-radius: 3px; background: color-mix(in srgb,#F59E0B 10%,transparent); display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; }
.quote-cover img { width: 100%; height: 100%; object-fit: cover; }
.quote-cover svg { color: #F59E0B; }
.quote-book { display: block; font-size: 11px; font-weight: 600; color: #F59E0B; }
.quote-author { display: block; font-size: 10px; color: var(--muted); margin-top: 1px; }
.repost-embed { background: var(--surface-var); border: 0.5px solid var(--divider); border-radius: 13px; padding: 12px; margin-bottom: 8px; cursor: pointer; transition: border-color 0.15s; display: flex; flex-direction: column; gap: 5px; }
.repost-embed:hover { border-color: var(--primary); }
.repost-label { display: flex; align-items: center; gap: 4px; font-size: 9px; font-weight: 700; color: var(--primary); letter-spacing: 0.8px; text-transform: uppercase; }
.repost-author { display: flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 600; color: var(--on-surface); }
.repost-av { width: 16px; height: 16px; border-radius: 50%; background: var(--surface); overflow: hidden; display: flex; align-items: center; justify-content: center; font-size: 8px; font-weight: 700; color: var(--on-bg); }
.repost-av img { width: 100%; height: 100%; object-fit: cover; }
.repost-title { font-size: 13px; font-weight: 600; color: var(--on-bg); }
.repost-chapter { font-size: 12px; color: var(--muted); }
.repost-text { font-size: 13px; color: var(--on-surface); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.repost-img { width: 100%; height: 120px; object-fit: cover; border-radius: 8px; }
.achievement-card { border-radius: 16px; overflow: hidden; margin-bottom: 8px; }
.achievement-inner { background: linear-gradient(135deg,#F5A623,#E8871E,#D9691B); padding: 18px; display: flex; flex-direction: column; gap: 10px; }
.achievement-top { display: flex; align-items: center; gap: 8px; }
.achievement-trophy { font-size: 28px; }
.achievement-level { font-size: 18px; font-weight: 900; color: white; }
.achievement-stats { display: flex; gap: 20px; }
.achievement-stats .ach-num { display: block; font-size: 20px; font-weight: 700; color: white; }
.achievement-stats .ach-lbl { font-size: 11px; color: rgba(255,255,255,0.85); }
.achievement-caption { font-size: 11.5px; font-weight: 500; color: rgba(255,255,255,0.9); }
.actions { display: flex; align-items: center; margin-top: 4px; }
.act-btn { display: flex; align-items: center; gap: 5px; padding: 7px 10px; border-radius: 20px; color: var(--muted); font-size: 13px; font-weight: 500; cursor: pointer; border: none; background: transparent; transition: color 0.15s, background 0.15s, transform 0.1s; font-family: inherit; }
.act-btn:hover { background: var(--surface-var); }
.act-btn:active { transform: scale(0.92); }
.act-btn.liked { color: #FF3A5C; }
.act-btn.liked:hover { background: rgba(255,58,92,0.1); }
.act-btn.save-btn.saved { color: #F59E0B; }
.act-spacer { flex: 1; }
.likes-pill { background: none; border: none; color: var(--muted); font-size: 12px; font-weight: 500; cursor: pointer; padding: 4px 6px; border-radius: 12px; font-family: inherit; transition: background 0.1s; }
.likes-pill:hover { background: var(--surface-var); }

/* Daha fazla yükle */
.load-more-wrap { padding: 8px 12px 4px; display: flex; justify-content: center; }
.load-more-btn { padding: 12px 28px; border: 1.5px solid var(--divider); border-radius: 99px; background: var(--surface); color: var(--primary); font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; display: flex; align-items: center; gap: 8px; transition: border-color 0.15s, background 0.15s; }
.load-more-btn:hover { border-color: var(--primary); background: color-mix(in srgb,var(--primary) 6%,var(--surface)); }

/* Okuma listesi */
.rl-section { padding: 12px 16px; }
.rl-header { font-size: 13px; font-weight: 700; color: var(--muted); margin: 0 0 10px; display: flex; align-items: center; gap: 6px; }
.rl-header span { background: var(--surface-var); border-radius: 99px; padding: 1px 7px; font-size: 11px; }
.rl-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 0.5px solid var(--divider); }
.rl-cover { width: 36px; height: 50px; border-radius: 4px; object-fit: cover; flex-shrink: 0; }
.rl-cover-ph { background: var(--surface-var); display: flex; align-items: center; justify-content: center; font-size: 18px; }
.rl-info { display: flex; flex-direction: column; gap: 3px; }
.rl-book { font-size: 14px; font-weight: 600; color: var(--on-bg); }
.rl-author { font-size: 12px; color: var(--muted); }

/* Boş state */
/* .empty-tab → EmptyState bileşenine taşındı */
.compose-cta-sm {
  display: inline-block; margin-top: 4px; padding: 8px 18px;
  background: var(--primary); color: #fff; border-radius: 20px;
  text-decoration: none; font-weight: 700; font-size: 13px;
  border: none; cursor: pointer; font-family: inherit;
}
.empty-link { color: var(--primary); font-weight: 600; font-size: 13px; text-decoration: none; }

/* Not found */
.not-found { display: flex; flex-direction: column; align-items: center; padding: 80px 20px; gap: 12px; text-align: center; }
.not-found h3 { font-size: 17px; color: var(--on-bg); margin: 0; }
.not-found p { font-size: 13px; color: var(--muted); margin: 0; }
.back-btn { background: none; border: none; color: var(--primary); font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }

/* Sheet */
.sheet-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 300; }
.sheet { position: fixed; bottom: 0; left: 0; right: 0; max-width: 600px; margin: 0 auto; background: var(--surface); border-radius: 20px 20px 0 0; z-index: 301; display: flex; flex-direction: column; max-height: 70vh; }
.sheet-handle { width: 40px; height: 4px; background: var(--divider); border-radius: 2px; margin: 12px auto 4px; flex-shrink: 0; }
.sheet-header { display: flex; align-items: center; justify-content: space-between; padding: 0 16px 12px; border-bottom: 1px solid var(--divider); flex-shrink: 0; }
.sheet-title { font-size: 15px; font-weight: 700; color: var(--on-bg); }
.sheet-count { font-size: 12px; color: var(--muted); margin-left: 4px; font-weight: 400; }
.sheet-close { background: none; border: none; color: var(--muted); font-size: 18px; cursor: pointer; }
.sheet-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.sheet-loading { display: flex; justify-content: center; padding: 24px; }
.sheet-empty { text-align: center; color: var(--muted); font-size: 14px; padding: 24px 0; }
.user-row { display: flex; align-items: center; gap: 10px; padding: 10px 16px; text-decoration: none; transition: background 0.1s; }
.user-row:hover { background: var(--surface-var); }
.user-av { width: 40px; height: 40px; border-radius: 50%; background: var(--surface-var); overflow: hidden; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 700; color: var(--on-bg); flex-shrink: 0; }
.user-av img { width: 100%; height: 100%; object-fit: cover; }
.user-name { flex: 1; font-size: 14px; font-weight: 600; color: var(--on-bg); }

/* Skeleton */
.cover-sk { width: 100%; height: 120px; background: var(--shimmer); animation: shimmer 1.4s ease-in-out infinite; }
.header-sk { display: flex; gap: 12px; padding: 16px; }
.av-sk { width: 78px; height: 78px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }
.sk-lines { flex: 1; display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }
.sk-line { height: 13px; background: var(--shimmer); border-radius: 6px; animation: shimmer 1.4s ease-in-out infinite; }
.post-sk { display: flex; gap: 10px; padding: 14px 16px; }
.sk-av-sm { width: 36px; height: 36px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }

/* Spinner */
.spinner { width: 18px; height: 18px; border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff; border-radius: 50%; animation: spin 0.7s linear infinite; }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes shimmer { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }


/* ── Kitaplar sekmesi ──────────────────────────────────────── */
.books-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px 8px;
}
.books-title { font-size: 14px; font-weight: 600; color: var(--on-bg); }
.add-book-btn {
  display: flex; align-items: center; gap: 5px; padding: 6px 12px;
  background: var(--primary); color: #fff; border: none; border-radius: 20px;
  font-size: 12px; font-weight: 600; cursor: pointer; font-family: inherit;
  transition: opacity 0.15s;
}
.add-book-btn:hover { opacity: 0.85; }
.empty-link-btn {
  background: none; border: none; color: var(--primary); font-size: 14px;
  cursor: pointer; font-family: inherit; padding: 0;
}

.rh-stat-btn {
  background: none; border: none; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  color: inherit; padding: 4px 8px; border-radius: 10px;
  transition: background 0.15s;
}
.rh-stat-btn:hover { background: rgba(255,255,255,0.08); }

.quotes-sheet {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: min(600px, 100vw);
  background: var(--surface); border-radius: 24px 24px 0 0;
  box-shadow: 0 -4px 32px rgba(0,0,0,0.2);
  z-index: 401; max-height: 70vh; display: flex; flex-direction: column;
  animation: sheet-up 0.25s ease;
}
@keyframes sheet-up {
  from { transform: translateX(-50%) translateY(100%); }
  to   { transform: translateX(-50%) translateY(0); }
}
.quotes-sheet-handle {
  width: 36px; height: 4px; border-radius: 2px;
  background: var(--divider); margin: 10px auto 4px;
  flex-shrink: 0;
}
.quotes-sheet-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 16px 10px; flex-shrink: 0;
}
.quotes-sheet-title { font-weight: 700; font-size: 1rem; color: var(--on-bg); }
.quotes-sheet-divider { height: 1px; background: var(--divider); flex-shrink: 0; }
.quotes-sheet-empty {
  display: flex; align-items: center; justify-content: center;
  height: 120px; color: var(--muted); font-size: 0.9rem;
}
.quotes-sheet-list { overflow-y: auto; flex: 1; }
.quotes-sheet-item {
  display: block; padding: 12px 16px;
  text-decoration: none; color: inherit;
  transition: background 0.12s;
}
.quotes-sheet-item:hover { background: var(--surface-var); }
.qsi-text {
  font-size: 0.88rem; font-style: italic; line-height: 1.55;
  color: var(--on-bg); margin: 0 0 4px;
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden;
}
.qsi-meta { font-size: 0.75rem; color: #D97706; font-weight: 500; margin: 0; }

.quotes-list { display: flex; flex-direction: column; }
.quote-row {
  display: flex; gap: 12px; padding: 14px 16px;
  border-bottom: 1px solid var(--divider);
  text-decoration: none; color: inherit;
  transition: background 0.12s;
}
.quote-row:hover { background: var(--surface-var); }
.quote-row:last-child { border-bottom: none; }
.quote-row-icon {
  font-size: 2rem; line-height: 1; color: rgba(217,119,6,0.3);
  font-family: Georgia, serif; flex-shrink: 0; margin-top: -4px;
}
.quote-row-body { flex: 1; min-width: 0; }
.quote-row-text {
  font-size: 0.88rem; font-style: italic; line-height: 1.55;
  color: var(--on-bg); margin: 0 0 6px;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.quote-row-meta { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.quote-row-book  { font-size: 0.75rem; color: var(--primary); font-weight: 600; }
.quote-row-author{ font-size: 0.72rem; color: var(--muted); }

.books-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; padding: 12px 16px; }
.book-card {
  display: flex; gap: 12px; padding: 12px 16px;
  border-bottom: 1px solid var(--divider); transition: background 0.1s;
}
.book-card:last-child { border-bottom: none; }
.book-card:hover { background: var(--surface-var); }

.book-cover-wrap { width: 100%; overflow: hidden; }
.book-cover {
  width: 100%; aspect-ratio: 2/3; object-fit: cover;
}
.book-cover-ph {
  width: 100%; aspect-ratio: 2/3;
  background: color-mix(in srgb, var(--primary) 10%, var(--surface-var));
  display: flex; align-items: center; justify-content: center;
  color: var(--primary); opacity: 0.7;
}
.book-info { display: flex; flex-direction: column; gap: 3px; padding: 8px 10px 10px; }
.book-type-badge {
  display: inline-block; font-size: 9px; font-weight: 700;
  border-radius: 4px; padding: 2px 6px; margin-bottom: 4px;
}
.book-type-badge.serial { background: color-mix(in srgb, var(--primary) 15%, transparent); color: var(--primary); }
.book-type-badge.book   { background: color-mix(in srgb, #D97706 15%, transparent); color: #D97706; }
.book-title { font-size: 13px; font-weight: 600; color: var(--on-bg); line-height: 1.3; }
.book-author { font-size: 12px; color: var(--primary); }
.book-meta-row { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 3px; }
.book-meta-chip {
  font-size: 11px; color: var(--muted); background: var(--surface-var);
  border-radius: 6px; padding: 2px 7px;
}
.book-synopsis { font-size: 12px; color: var(--muted); line-height: 1.5; margin-top: 4px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

/* ── Modal (genel) ─────────────────────────────────────────── */
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.52);
  z-index: 400; backdrop-filter: blur(2px);
}
.modal-card {
  position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%);
  width: min(94vw, 500px); max-height: 88vh; overflow-y: auto;
  background: var(--surface); border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.25); z-index: 401;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 18px; border-bottom: 1px solid var(--divider);
  position: sticky; top: 0; background: var(--surface); z-index: 1;
}
.modal-title { font-size: 16px; font-weight: 700; color: var(--on-bg); }
.modal-body { padding: 16px 18px; display: flex; flex-direction: column; gap: 8px; }
.modal-label { font-size: 11px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 2px; }
.modal-input {
  width: 100%; background: var(--surface-var); border: 1px solid var(--divider);
  border-radius: 10px; padding: 10px 12px; font-size: 14px; color: var(--on-bg);
  outline: none; font-family: inherit; box-sizing: border-box; transition: border-color 0.15s;
}
.modal-input:focus { border-color: var(--primary); }
.modal-input::placeholder { color: var(--muted); }
.modal-textarea {
  width: 100%; background: var(--surface-var); border: 1px solid var(--divider);
  border-radius: 10px; padding: 10px 12px; font-size: 14px; color: var(--on-bg);
  outline: none; font-family: inherit; resize: vertical; line-height: 1.6;
  box-sizing: border-box; transition: border-color 0.15s;
}
.modal-textarea:focus { border-color: var(--primary); }
.modal-textarea.quote-ta { font-style: italic; }
.modal-textarea::placeholder { color: var(--muted); }
.modal-row { display: flex; gap: 10px; }
.modal-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.modal-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  padding: 12px 18px; border-top: 1px solid var(--divider);
  position: sticky; bottom: 0; background: var(--surface);
}
.modal-cancel {
  padding: 9px 18px; border-radius: 10px; background: none; border: none;
  font-size: 14px; color: var(--muted); cursor: pointer; font-family: inherit;
}
.modal-cancel:hover { color: var(--on-bg); }
.modal-save {
  padding: 9px 22px; border-radius: 10px; background: var(--primary); color: #fff;
  border: none; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit;
  transition: opacity 0.15s;
}
.modal-save:disabled { opacity: 0.4; cursor: not-allowed; }
.modal-error { font-size: 13px; color: var(--error, #ef4444); margin-top: 4px; }

/* Kapak önizleme */
.cover-preview-wrap { display: flex; justify-content: center; margin-top: 4px; }
.cover-preview { width: 64px; height: 96px; object-fit: cover; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.15); }

/* ── Alıntı modal — kütüphane kitap listesi ────────────────── */
.lib-book-list {
  border: 1px solid var(--divider); border-radius: 12px;
  overflow: hidden; max-height: 200px; overflow-y: auto;
}
.lib-book-item {
  display: flex; align-items: center; gap: 10px; width: 100%;
  background: none; border: none; border-bottom: 1px solid var(--divider);
  padding: 9px 12px; cursor: pointer; text-align: left; font-family: inherit;
  transition: background 0.1s;
}
.lib-book-item:last-child { border-bottom: none; }
.lib-book-item:hover { background: var(--surface-var); }
.lib-book-cover {
  width: 28px; height: 42px; border-radius: 3px; object-fit: cover; flex-shrink: 0;
}
.lib-book-cover.no-cover {
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  display: flex; align-items: center; justify-content: center; color: var(--primary);
}
.lib-book-text { display: flex; flex-direction: column; min-width: 0; }
.lib-book-title { font-size: 13px; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.lib-book-meta { font-size: 11px; color: var(--muted); margin-top: 1px; }

.or-divider { text-align: center; font-size: 12px; color: var(--muted); margin: 8px 0 4px; }
.hint-text { font-size: 12px; color: var(--muted); margin-top: 4px; }

/* Seçili kitap göstergesi */
.selected-book-row {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  background: color-mix(in srgb, var(--primary) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--primary) 25%, transparent);
  border-radius: 10px;
}
.selected-book-cover {
  width: 32px; height: 48px; border-radius: 4px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.selected-book-cover.no-cover { color: #F59E0B; }
.selected-book-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.selected-book-title { font-size: 13px; font-weight: 600; color: var(--on-bg); }
.selected-book-author { font-size: 11px; color: var(--muted); }
.library-badge { font-size: 11px; color: var(--primary); font-weight: 600; }
.clear-book-btn { background: none; border: none; color: var(--muted); cursor: pointer; font-size: 14px; padding: 4px; }
.clear-book-btn:hover { color: var(--on-bg); }

/* ── Rozetler ───────────────────────────────────────────────────────────── */
.badges-row {
  display: flex; gap: 8px; overflow-x: auto; padding: 8px 0 4px;
  scrollbar-width: none;
}
.badges-row::-webkit-scrollbar { display: none; }
.badge-item {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  min-width: 60px; background: none; border: none; cursor: pointer;
  padding: 6px 4px; border-radius: 12px; transition: background 0.15s;
  font-family: inherit;
}
.badge-item:hover { background: var(--surface-var); }
.badge-emoji { font-size: 26px; line-height: 1; }
.badge-label {
  font-size: 9px; color: var(--muted); text-align: center;
  line-height: 1.3; white-space: nowrap; overflow: hidden;
  text-overflow: ellipsis; max-width: 58px;
}
.badge-info-bar {
  display: flex; align-items: center; gap: 10px;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  border: 1px solid color-mix(in srgb, #F59E0B 30%, transparent);
  border-radius: 12px; padding: 10px 12px; margin-top: 6px;
  animation: fadeup 0.15s ease;
}
.badge-info-emoji { font-size: 24px; flex-shrink: 0; }
.badge-info-bar div { flex: 1; }
.badge-info-bar strong { display: block; font-size: 13px; color: var(--on-bg); font-weight: 700; }
.badge-info-bar p { font-size: 12px; color: var(--muted); margin: 2px 0 0; }
.badge-info-close {
  background: none; border: none; color: var(--muted);
  cursor: pointer; font-size: 14px; flex-shrink: 0; padding: 4px;
}

/* ── ReadBooksSheet ─────────────────────────────────────────────────────── */
.rl-sheet-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; border-bottom: 0.5px solid var(--divider);
  cursor: pointer; transition: background 0.1s;
}
.rl-sheet-item:hover { background: var(--surface-var); }
.rl-sheet-cover {
  width: 38px; height: 54px; border-radius: 4px; object-fit: cover; flex-shrink: 0;
}
.rl-sheet-cover-ph {
  background: var(--surface-var); display: flex; align-items: center;
  justify-content: center; font-size: 20px;
}
.rl-sheet-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.rl-sheet-title { font-size: 14px; font-weight: 600; color: var(--on-bg); }
.rl-sheet-author { font-size: 12px; color: var(--muted); }

</style>
