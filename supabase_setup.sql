-- =====================================================================
-- TRACKIT: run this ONCE in Supabase > SQL Editor > New query.
-- Part 1: tables, triggers and Row Level Security.
-- Part 2 (at the bottom): the delete_my_account() function used by
--         Profile > Privacy & Security > Delete account.
-- If you already ran part 1 earlier, only run part 2.
-- =====================================================================

create table public.profiles (
  id            uuid primary key references auth.users (id) on delete cascade,
  display_name  text,
  currency_code text not null default 'IDR',
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create table public.transactions (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  type         text not null check (type in ('plus', 'minus')),
  amount       numeric(18, 2) not null check (amount > 0),
  description  text check (description is null or char_length(description) <= 200),
  occurred_at  timestamptz not null default now(),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index transactions_user_occurred_idx
  on public.transactions (user_id, occurred_at desc);

create table public.easy_inputs (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name        text not null check (char_length(btrim(name)) > 0 and char_length(name) <= 60),
  amount      numeric(18, 2) not null check (amount > 0),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index easy_inputs_user_idx
  on public.easy_inputs (user_id, created_at);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger set_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();
create trigger set_updated_at before update on public.transactions
  for each row execute function public.set_updated_at();
create trigger set_updated_at before update on public.easy_inputs
  for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    coalesce(nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''), split_part(new.email, '@', 1))
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

alter table public.profiles     enable row level security;
alter table public.transactions enable row level security;
alter table public.easy_inputs  enable row level security;

create policy "profiles_select_own" on public.profiles
  for select to authenticated using ((select auth.uid()) = id);
create policy "profiles_insert_own" on public.profiles
  for insert to authenticated with check ((select auth.uid()) = id);
create policy "profiles_update_own" on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy "transactions_select_own" on public.transactions
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "transactions_insert_own" on public.transactions
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "transactions_update_own" on public.transactions
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "transactions_delete_own" on public.transactions
  for delete to authenticated using ((select auth.uid()) = user_id);

create policy "easy_inputs_select_own" on public.easy_inputs
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "easy_inputs_insert_own" on public.easy_inputs
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "easy_inputs_update_own" on public.easy_inputs
  for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "easy_inputs_delete_own" on public.easy_inputs
  for delete to authenticated using ((select auth.uid()) = user_id);

revoke all on public.profiles, public.transactions, public.easy_inputs from anon;

-- =====================================================================
-- PART 2: account deletion (deletes only the caller's own account;
-- records, presets and profile are removed by ON DELETE CASCADE)
-- =====================================================================
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'Not authenticated' using errcode = '28000';
  end if;
  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
