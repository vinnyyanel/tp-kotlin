-- ============================================================
-- SCHEMA MESSAGERIE - Supabase (Postgres)
-- À coller dans : Dashboard Supabase > SQL Editor > Run
-- ============================================================

-- 1. PROFILS (lié à auth.users géré par Supabase Auth)
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null,
  avatar_url text,
  is_ai boolean not null default false,        -- indicateur visuel humain / agent IA
  created_at timestamptz not null default now()
);

-- Création automatique du profil à l'inscription
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
begin
  insert into public.profiles (id, username)
  values (new.id, coalesce(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)));
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- 2. CONVERSATIONS
create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  is_ai_chat boolean not null default false,   -- mode agent IA
  ai_persona text,                              -- 'general', 'juridique', 'technique'...
  created_at timestamptz not null default now(),
  last_message_at timestamptz not null default now()
);

-- 3. PARTICIPANTS (table de jointure user <-> conversation)
create table public.participants (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

-- 4. MESSAGES
create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid references public.profiles(id) on delete set null, -- null = IA
  content text,                                 -- texte (chiffré côté client en mode humain)
  media_url text,                               -- URL Storage (photo / audio)
  media_type text check (media_type in ('image', 'audio')),
  status text not null default 'sent' check (status in ('sent', 'delivered', 'read')),
  is_from_ai boolean not null default false,
  created_at timestamptz not null default now()
);

create index idx_messages_conversation on public.messages(conversation_id, created_at);

-- Mise à jour de last_message_at pour trier la liste de conversations
create or replace function public.bump_conversation()
returns trigger language plpgsql as $$
begin
  update public.conversations set last_message_at = now() where id = new.conversation_id;
  return new;
end;
$$;

create trigger on_message_insert
  after insert on public.messages
  for each row execute function public.bump_conversation();

-- ============================================================
-- ROW LEVEL SECURITY : chaque user ne voit QUE ses données
-- ============================================================
alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.participants enable row level security;
alter table public.messages enable row level security;

-- Fonction utilitaire : suis-je membre de cette conversation ?
create or replace function public.is_member(conv_id uuid)
returns boolean language sql security definer stable as $$
  select exists (
    select 1 from public.participants
    where conversation_id = conv_id and user_id = auth.uid()
  );
$$;

-- Profils : lisibles par tous les connectés (pour la liste de contacts)
create policy "profiles_select" on public.profiles
  for select to authenticated using (true);
create policy "profiles_update_own" on public.profiles
  for update to authenticated using (id = auth.uid());

-- Conversations : visibles/créables par les membres
create policy "conv_select" on public.conversations
  for select to authenticated using (public.is_member(id));
create policy "conv_insert" on public.conversations
  for insert to authenticated with check (true);

-- Participants
create policy "part_select" on public.participants
  for select to authenticated using (public.is_member(conversation_id));
create policy "part_insert" on public.participants
  for insert to authenticated with check (user_id = auth.uid() or public.is_member(conversation_id));

-- Messages : lecture et écriture réservées aux membres
create policy "msg_select" on public.messages
  for select to authenticated using (public.is_member(conversation_id));
create policy "msg_insert" on public.messages
  for insert to authenticated
  with check (public.is_member(conversation_id) and (sender_id = auth.uid() or is_from_ai));
create policy "msg_update_status" on public.messages
  for update to authenticated using (public.is_member(conversation_id));

-- ============================================================
-- REALTIME : activer la diffusion des changements
-- ============================================================
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.conversations;

-- ============================================================
-- STORAGE : bucket pour les médias
-- ============================================================
insert into storage.buckets (id, name, public) values ('media', 'media', true);

create policy "media_upload" on storage.objects
  for insert to authenticated with check (bucket_id = 'media');
create policy "media_read" on storage.objects
  for select using (bucket_id = 'media');
