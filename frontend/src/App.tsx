import {
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type SyntheticEvent,
} from "react";
import {
  ArrowRight,
  Candy,
  Check,
  Heart,
  Loader2,
  LockKeyhole,
  LogOut,
  MapPin,
  Plus,
  Search,
  X,
} from "lucide-react";
import {
  api,
  ApiError,
  type User,
  type Listing,
  type ListingInput,
  type Claim,
  type Registration,
} from "./api";

type Tab = "all" | "mine" | "claims";
type Dialog = "share" | "login" | "register" | "detail" | null;
const statusLabels = {
  AVAILABLE: "Available",
  RESERVED: "Reserved",
  COMPLETED: "Shared",
  WITHDRAWN: "Withdrawn",
  CANCELLED: "Cancelled",
};
const formatDate = (value: string) =>
  new Date(value).toLocaleDateString("en-CA", {
    month: "short",
    day: "numeric",
  });

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>("all");
  const [dialog, setDialog] = useState<Dialog>(null);
  const [listings, setListings] = useState<Listing[]>([]);
  const [mine, setMine] = useState<Listing[]>([]);
  const [claims, setClaims] = useState<Claim[]>([]);
  const [detail, setDetail] = useState<Listing | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [editing, setEditing] = useState<Listing | null>(null);
  const [query, setQuery] = useState("");
  const [region, setRegion] = useState("");
  const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  function showError(err: unknown) {
    setError(
      err instanceof Error
        ? err.message
        : "Something went wrong. Please try again.",
    );
    if (err instanceof ApiError && err.status === 401) {
      setUser(null);
      setMine([]);
      setClaims([]);
      setDetail(null);
      setTab("all");
      setDialog("login");
    }
  }

  useEffect(() => {
    let active = true;
    api
      .me()
      .then((account) => {
        if (active) setUser(account);
      })
      .catch((err) => {
        if (active && !(err instanceof ApiError && err.status === 401))
          setError("Could not connect. Check the backend and try Refresh.");
      })
      .finally(() => {
        if (active) setReady(true);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!ready) return;
    let active = true;
    setLoading(true);
    const timer = window.setTimeout(() => {
      Promise.all([
        api.listings(query, region),
        user ? api.myListings() : Promise.resolve([]),
        user ? api.myClaims() : Promise.resolve([]),
      ])
        .then(([publicNotes, ownNotes, pickups]) => {
          if (active) {
            setListings(publicNotes);
            setMine(ownNotes);
            setClaims(pickups);
          }
        })
        .catch((err) => {
          if (active) showError(err);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 200);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [ready, user?.id, query, region, version]);

  useEffect(() => {
    if (selectedId === null || dialog !== "detail") return;
    let active = true;
    setDetail(null);
    api
      .detail(selectedId)
      .then((note) => {
        if (active) setDetail(note);
      })
      .catch((err) => {
        if (active) showError(err);
      });
    return () => {
      active = false;
    };
  }, [selectedId, dialog, user?.id, version]);

  function openShare(note: Listing | null = null) {
    setError("");
    setNotice("");
    setEditing(note);
    setDialog(user ? "share" : "login");
  }
  function openDetail(id: number) {
    setError("");
    setSelectedId(id);
    setDetail(null);
    setDialog("detail");
  }
  function changeTab(next: Tab) {
    setError("");
    setNotice("");
    if (next !== "all" && !user) setDialog("login");
    else setTab(next);
  }
  async function perform(
    action: () => Promise<unknown>,
    message: string,
    nextTab?: Tab,
  ) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setDialog(null);
      setNotice(message);
      setVersion((value) => value + 1);
      if (nextTab) setTab(nextTab);
    } catch (err) {
      showError(err);
    } finally {
      setBusy(false);
    }
  }
  async function authenticate(input: Registration, registering: boolean) {
    await perform(
      async () => {
        if (registering) await api.register(input);
        setUser(await api.login(input.email, input.password));
      },
      registering ? "Welcome! You're ready to share." : "Welcome back.",
    );
  }

  const visibleCount =
    tab === "all"
      ? listings.length
      : tab === "mine"
        ? mine.length
        : claims.length;
  const errorMessage = error && (
    <div className="message error" role="alert">
      {error}
      <button
        type="button"
        onClick={() => setError("")}
        aria-label="Dismiss error"
      >
        <X size={16} />
      </button>
    </div>
  );

  return (
    <div className="app">
      <header className="header">
        <button
          className="brand"
          onClick={() => changeTab("all")}
          aria-label="Halloween CandyShare home"
        >
          <span className="brand-icon">
            <Candy size={25} />
          </span>
          <span>
            <small>HALLOWEEN</small>CandyShare<span className="orange">.</span>
          </span>
        </button>
        <div className="account-actions">
          {user ? (
            <>
              <span className="greeting">Hi, {user.name}</span>
              <button
                className="icon-button"
                disabled={busy}
                aria-label="Sign out"
                title="Sign out"
                onClick={() =>
                  void perform(
                    async () => {
                      await api.logout();
                      setUser(null);
                      setMine([]);
                      setClaims([]);
                      setDetail(null);
                    },
                    "Signed out.",
                    "all",
                  )
                }
              >
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <button
              className="text-button"
              disabled={!ready}
              onClick={() => {
                setError("");
                setDialog("login");
              }}
            >
              Sign in
            </button>
          )}
          <button className="button primary" onClick={() => openShare()}>
            <Plus size={18} /> Share a note
          </button>
        </div>
      </header>
      <main>
        <section className="intro">
          <span className="eyebrow">THE NEIGHBORHOOD CANDY BOARD</span>
          <h1>
            After Halloween,
            <br />
            <span>Give Your Extras.</span> Take What You Need.
          </h1>
          <p>
            Share your extra unopened Halloween candy with people and
            organizations in your community.
          </p>
        </section>
        <div className="board-toolbar">
          <nav className="tabs" aria-label="Candy notes">
            <button
              aria-current={tab === "all" ? "page" : undefined}
              onClick={() => changeTab("all")}
            >
              All notes
            </button>
            <button
              aria-current={tab === "mine" ? "page" : undefined}
              onClick={() => changeTab("mine")}
            >
              My notes
            </button>
            <button
              aria-current={tab === "claims" ? "page" : undefined}
              onClick={() => changeTab("claims")}
            >
              My pickups
            </button>
          </nav>
          <span className="board-caption">
            Free candy. Real neighbors. <Heart size={13} />
          </span>
        </div>
        {tab === "all" && (
          <div className="filters">
            <label>
              <Search size={17} />
              <input
                value={query}
                maxLength={120}
                onChange={(event) => setQuery(event.target.value)}
                aria-label="Search notes"
                placeholder="Search notes…"
              />
            </label>
            <label>
              <MapPin size={17} />
              <input
                value={region}
                maxLength={100}
                onChange={(event) => setRegion(event.target.value)}
                aria-label="Filter by full pickup area"
                placeholder="Area, e.g. Burnaby"
              />
            </label>
            {(query || region) && (
              <button
                className="text-button"
                onClick={() => {
                  setQuery("");
                  setRegion("");
                }}
              >
                Clear
              </button>
            )}
          </div>
        )}
        <div className="board-meta">
          <span>
            {loading
              ? "Loading…"
              : `${visibleCount} ${tab === "claims" ? "pickup records" : "notes"}`}
            {tab === "all" && region ? ` · ${region}` : ""}
          </span>
          <button
            className="text-button"
            onClick={() => {
              setError("");
              setVersion((value) => value + 1);
            }}
          >
            Refresh
          </button>
        </div>
        {!dialog && errorMessage}
        {notice && (
          <div className="message success" role="status">
            <Check size={16} />
            {notice}
            <button
              onClick={() => setNotice("")}
              aria-label="Dismiss notification"
            >
              <X size={16} />
            </button>
          </div>
        )}
        {loading ? (
          <div className="loading" role="status">
            <Loader2 className="spin" size={22} /> Loading the board…
          </div>
        ) : visibleCount === 0 ? (
          <div className="empty">
            <span>
              <Candy size={36} strokeWidth={1.3} />
            </span>
            <h2>
              {tab === "claims"
                ? "No pickups yet."
                : "A little note can make someone's day."}
            </h2>
            <p>
              {query || region
                ? "No matches. Try a different note or pickup area."
                : tab === "claims"
                  ? "Find a note on the board and reserve a bag."
                  : "Write what you have, add your area, and share."}
            </p>
            <button
              className="button primary"
              onClick={() =>
                tab === "claims" ? changeTab("all") : openShare()
              }
            >
              {tab === "claims" ? "Browse notes" : "Write a note"}
              <ArrowRight size={16} />
            </button>
          </div>
        ) : (
          <div className="note-grid">
            {tab !== "claims"
              ? (tab === "all" ? listings : mine).map((note) => (
                  <NoteCard key={note.id} note={note} status={note.status}>
                    {tab === "mine" ? (
                      <>
                        {note.status === "AVAILABLE" && (
                          <>
                            <button
                              onClick={() => openShare(note)}
                              disabled={busy}
                            >
                              Edit
                            </button>
                            <button
                              className="muted-button"
                              disabled={busy}
                              onClick={() => {
                                if (
                                  window.confirm(
                                    "Withdraw this note from the board?",
                                  )
                                )
                                  void perform(
                                    () => api.withdraw(note.id),
                                    "Note withdrawn.",
                                  );
                              }}
                            >
                              Withdraw
                            </button>
                          </>
                        )}
                        {note.status === "RESERVED" && (
                          <>
                            <PickupContact note={note} owner />
                            <button
                              className="note-primary"
                              disabled={busy}
                              onClick={() => {
                                if (
                                  window.confirm(
                                    "Has the whole bag been handed over?",
                                  )
                                )
                                  void perform(
                                    () => api.complete(note.id),
                                    "Marked as shared. Thank you!",
                                  );
                              }}
                            >
                              <Check size={15} /> Confirm handoff
                            </button>
                          </>
                        )}
                        {note.status === "COMPLETED" && (
                          <span className="quiet-note">
                            Thanks for passing it on.
                          </span>
                        )}
                        {note.status === "WITHDRAWN" && (
                          <span className="quiet-note">
                            Kept here for your records.
                          </span>
                        )}
                      </>
                    ) : (
                      <button onClick={() => openDetail(note.id)}>
                        Read note <ArrowRight size={15} />
                      </button>
                    )}
                  </NoteCard>
                ))
              : claims.map((claim) => (
                  <NoteCard
                    key={claim.id}
                    note={claim.listing}
                    status={claim.status}
                    extra={`Reserved ${formatDate(claim.createdAt)}`}
                  >
                    {claim.status === "RESERVED" ? (
                      <>
                        <PickupContact note={claim.listing} />
                        <button
                          className="muted-button"
                          disabled={busy}
                          onClick={() => {
                            if (
                              window.confirm(
                                "Cancel this pickup and reopen the note?",
                              )
                            )
                              void perform(
                                () => api.cancel(claim.id),
                                "Pickup cancelled. The bag is available again.",
                              );
                          }}
                        >
                          Cancel pickup
                        </button>
                      </>
                    ) : (
                      <span className="quiet-note">
                        {claim.status === "COMPLETED"
                          ? "A little sweetness, shared."
                          : "Your cancellation is saved."}
                      </span>
                    )}
                  </NoteCard>
                ))}
          </div>
        )}
        <p className="board-footnote">
          One note = one whole bag, always free. Arrange the exact pickup
          privately after reserving.
        </p>
      </main>
      <footer>
        <Candy size={16} />
        <span>Halloween CandyShare</span>
        <span className="footer-right">
          A little less waste. A little more kindness.
        </span>
      </footer>

      {dialog && (
        <Modal
          title={
            dialog === "share"
              ? editing
                ? "Edit your note"
                : "Leave a little sweetness"
              : dialog === "register"
                ? "Join the neighborhood"
                : dialog === "login"
                  ? "Welcome back"
                  : "A note from your neighbor"
          }
          onClose={() => {
            if (!busy) {
              setDialog(null);
              setError("");
            }
          }}
        >
          {errorMessage}
          {dialog === "share" && (
            <NoteForm
              key={editing?.id ?? "new"}
              initial={editing}
              busy={busy}
              onSave={(input) =>
                perform(
                  () =>
                    editing ? api.update(editing.id, input) : api.create(input),
                  editing ? "Note updated." : "Your note is on the board!",
                  "mine",
                )
              }
            />
          )}
          {(dialog === "login" || dialog === "register") && (
            <AuthForm
              key={dialog}
              registering={dialog === "register"}
              busy={busy}
              onSubmit={authenticate}
              onSwitch={() => {
                setError("");
                setDialog(dialog === "register" ? "login" : "register");
              }}
            />
          )}
          {dialog === "detail" &&
            (detail ? (
              <div className="note-detail">
                <StatusBadge status={detail.status} />
                <p className="note-body">{detail.notes}</p>
                <p className="note-location">
                  <MapPin size={16} />
                  {detail.region}
                </p>
                <p className="quiet-note">
                  Shared by {detail.ownerName} · {formatDate(detail.createdAt)}
                </p>
                {detail.ownerId === user?.id ? (
                  <button
                    className="button outline"
                    onClick={() => {
                      setDialog(null);
                      setTab("mine");
                    }}
                  >
                    Go to my notes
                  </button>
                ) : detail.status === "AVAILABLE" ? (
                  <button
                    className="button primary wide"
                    disabled={busy}
                    onClick={() =>
                      user
                        ? void perform(
                            () => api.reserve(detail.id),
                            "Reserved! Contact your neighbor in My pickups.",
                            "claims",
                          )
                        : setDialog("login")
                    }
                  >
                    {busy && <Loader2 size={16} className="spin" />}
                    {user ? "Reserve this bag" : "Sign in to reserve"}
                    <ArrowRight size={16} />
                  </button>
                ) : (
                  <p className="quiet-note">
                    This bag is {statusLabels[detail.status].toLowerCase()}.
                  </p>
                )}
                {detail.status === "RESERVED" && detail.handoff && (
                  <PickupContact
                    note={detail}
                    owner={detail.ownerId === user?.id}
                  />
                )}
                {!detail.handoff && (
                  <p className="privacy-note">
                    <LockKeyhole size={13} /> Your pickup partner's contact is
                    visible after reserving.
                  </p>
                )}
              </div>
            ) : (
              !error && (
                <div className="loading" role="status">
                  <Loader2 className="spin" size={20} /> Loading note…
                </div>
              )
            ))}
        </Modal>
      )}
    </div>
  );
}

function StatusBadge({ status }: { status: keyof typeof statusLabels }) {
  return (
    <span className={`status ${status.toLowerCase()}`}>
      <span />
      {statusLabels[status]}
    </span>
  );
}

function NoteCard({
  note,
  status,
  extra,
  children,
}: {
  note: Listing;
  status: keyof typeof statusLabels;
  extra?: string;
  children: ReactNode;
}) {
  return (
    <article className={`note-card paper-${note.id % 4}`}>
      <span className="note-pin" aria-hidden="true" />
      <div className="note-top">
        <StatusBadge status={status} />
        <span>{formatDate(note.createdAt)}</span>
      </div>
      <p className="note-body">{note.notes}</p>
      <div className="note-signature">
        <span className="note-location">
          <MapPin size={14} />
          {note.region}
        </span>
        <span>— {note.ownerName}</span>
      </div>
      {extra && <small className="quiet-note">{extra}</small>}
      <div className="note-actions">{children}</div>
    </article>
  );
}

function PickupContact({
  note,
  owner = false,
}: {
  note: Listing;
  owner?: boolean;
}) {
  if (!note.handoff) return null;
  return (
    <div className="pickup-contact">
      <strong>
        <LockKeyhole size={13} /> Private pickup contact
      </strong>
      <span>{owner ? note.handoff.claimantName : note.ownerName}</span>
      <p>{owner ? note.handoff.claimantContact : note.handoff.ownerContact}</p>
      {note.handoff.instructions && <p>{note.handoff.instructions}</p>}
    </div>
  );
}

function NoteForm({
  initial,
  busy,
  onSave,
}: {
  initial: Listing | null;
  busy: boolean;
  onSave: (input: ListingInput) => Promise<void>;
}) {
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [region, setRegion] = useState(initial?.region ?? "");
  function submit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    void onSave({ notes, region });
  }
  return (
    <form onSubmit={submit} className="simple-form">
      <label>
        Notes
        <textarea
          required
          maxLength={2000}
          rows={7}
          placeholder="A bag of unopened chocolate and gummies, about 30 pieces. Some contain peanuts. Pickup this weekend."
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
        />
      </label>
      <label>
        Pickup area
        <input
          required
          maxLength={100}
          placeholder="Burnaby"
          value={region}
          onChange={(event) => setRegion(event.target.value)}
        />
      </label>
      <p className="form-hint">
        Share unopened candy only. Notes are public, so keep phone numbers and
        exact addresses out. Arrange pickup privately after reserving.
      </p>
      <button type="submit" className="button primary wide" disabled={busy}>
        {busy ? <Loader2 className="spin" size={17} /> : <Plus size={17} />}
        {initial ? "Save note" : "Post my note"}
      </button>
    </form>
  );
}

function AuthForm({
  registering,
  busy,
  onSubmit,
  onSwitch,
}: {
  registering: boolean;
  busy: boolean;
  onSubmit: (input: Registration, registering: boolean) => Promise<void>;
  onSwitch: () => void;
}) {
  const [input, setInput] = useState<Registration>({
    name: "",
    email: "",
    password: "",
  });
  function submit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    void onSubmit(input, registering);
  }
  return (
    <form className="simple-form" onSubmit={submit}>
      <p className="form-hint">
        {registering
          ? "One account to share candy and reserve a bag. Your email is shared only with your pickup partner."
          : "Sign in to post a note or reserve candy."}
      </p>
      {registering && (
        <label>
          Name
          <input
            required
            maxLength={80}
            autoComplete="name"
            value={input.name}
            onChange={(event) =>
              setInput({ ...input, name: event.target.value })
            }
          />
        </label>
      )}
      <label>
        Email
        <input
          type="email"
          required
          maxLength={160}
          autoComplete="email"
          value={input.email}
          onChange={(event) =>
            setInput({ ...input, email: event.target.value })
          }
        />
      </label>
      <label>
        Password
        <input
          type="password"
          required
          minLength={registering ? 10 : undefined}
          maxLength={64}
          autoComplete={registering ? "new-password" : "current-password"}
          placeholder={registering ? "At least 10 characters" : ""}
          value={input.password}
          onChange={(event) =>
            setInput({ ...input, password: event.target.value })
          }
        />
      </label>
      <button className="button primary wide" disabled={busy} type="submit">
        {busy && <Loader2 className="spin" size={17} />}
        {registering ? "Create account" : "Sign in"}
        <ArrowRight size={17} />
      </button>
      <p className="auth-switch">
        {registering ? "Already have an account?" : "New to CandyShare?"}{" "}
        <button
          type="button"
          className="text-button"
          disabled={busy}
          onClick={onSwitch}
        >
          {registering ? "Sign in" : "Create an account"}
        </button>
      </p>
    </form>
  );
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialogRef.current?.focus();
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") closeRef.current();
      if (event.key !== "Tab") return;
      const elements = Array.from(
        dialogRef.current?.querySelectorAll<HTMLElement>(
          'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
        ) ?? [],
      );
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (!first) {
        event.preventDefault();
        return;
      }
      if (
        event.shiftKey &&
        (document.activeElement === first ||
          document.activeElement === dialogRef.current)
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last ||
          document.activeElement === dialogRef.current)
      ) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = oldOverflow;
      document.removeEventListener("keydown", onKey);
      previousFocus?.focus();
    };
  }, []);
  return (
    <div
      className="modal-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        tabIndex={-1}
        ref={dialogRef}
      >
        <div className="modal-header">
          <h2 id="dialog-title">{title}</h2>
          <button
            className="icon-button"
            onClick={onClose}
            aria-label="Close dialog"
          >
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
